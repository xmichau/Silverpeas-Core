/**
 * Copyright (C) 2000 - 2013 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of the GPL, you may
 * redistribute this Program in connection with Free/Libre Open Source Software ("FLOSS")
 * applications as described in Silverpeas's FLOSS exception. You should have received a copy of the
 * text describing the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/docs/core/legal/floss_exception.html"
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.silverpeas.importExport.versioning;

import com.silverpeas.form.importExport.FormTemplateImportExport;
import com.silverpeas.form.importExport.XMLModelContentType;
import com.silverpeas.util.FileUtil;
import com.silverpeas.util.ForeignPK;
import com.silverpeas.util.StringUtil;
import com.silverpeas.util.i18n.I18NHelper;
import com.stratelia.silverpeas.silverpeasinitialize.CallBackManager;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.beans.admin.UserDetail;
import com.stratelia.webactiv.util.ResourceLocator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.silverpeas.attachment.AttachmentServiceFactory;
import org.silverpeas.attachment.model.HistorisedDocument;
import org.silverpeas.attachment.model.SimpleAttachment;
import org.silverpeas.attachment.model.SimpleDocument;
import org.silverpeas.attachment.model.SimpleDocumentPK;
import org.silverpeas.attachment.model.UnlockContext;
import org.silverpeas.importExport.attachment.AttachmentDetail;
import org.silverpeas.importExport.attachment.AttachmentImportExport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author neysseri
 */
public class VersioningImportExport {

  private UserDetail user;
  private final ResourceLocator resources = new ResourceLocator(
      "org.silverpeas.importExport.settings.importSettings", "");

  public VersioningImportExport(UserDetail user) {
    this.user = user;
  }

  public int importDocuments(String objectId, String componentId, List<AttachmentDetail> attachments,
      int userId, boolean indexIt) throws RemoteException, IOException {
    return importDocuments(objectId, componentId, attachments, userId,
        DocumentVersion.TYPE_PUBLIC_VERSION, indexIt, null);
  }

  /**
   * @param objectId
   * @param componentId
   * @param attachments
   * @param userId
   * @param versionType
   * @param indexIt
   * @param topicId
   * @return
   * @throws RemoteException
   * @throws IOException
   */
  public int importDocuments(String objectId, String componentId, List<AttachmentDetail> attachments,
      int userId, int versionType, boolean indexIt, String topicId) throws RemoteException,
      IOException {
    SilverTrace.info("versioning", "VersioningImportExport.importDocuments()",
        "root.GEN_PARAM_VALUE", componentId);
    int nbFilesProcessed = 0;
    AttachmentImportExport attachmentImportExport =
        new AttachmentImportExport(UserDetail.getById(String.valueOf(userId)));
    ForeignPK pubPK = new ForeignPK(objectId, componentId);

    // get existing documents of object
    List<SimpleDocument> documents = AttachmentServiceFactory.getAttachmentService().
        listDocumentsByForeignKey(pubPK, null);
    for (AttachmentDetail attachment : attachments) {
      InputStream content = attachmentImportExport.getAttachmentContent(attachment);
      if (!StringUtil.isDefined(attachment.getAuthor())) {
        attachment.setAuthor(user.getId());
      }
      SimpleDocument document = isDocumentExist(documents, attachment);
      if (document != null) {
        document.edit(attachment.getAuthor());
        AttachmentServiceFactory.getAttachmentService().lock(document.getId(), attachment
            .getAuthor(), null);
        AttachmentServiceFactory.getAttachmentService().updateAttachment(document, content, indexIt,
            true);
        AttachmentServiceFactory.getAttachmentService().unlock(new UnlockContext(document.getId(),
            attachment.getAuthor(), null));
      } else {
        if (attachment.getCreationDate() == null) {
          attachment.setCreationDate(new Date());
        }
        HistorisedDocument version = new HistorisedDocument(new SimpleDocumentPK(null, componentId),
            objectId, -1, attachment.getAuthor(), new SimpleAttachment(attachment.getLogicalName(),
            attachment.getLanguage(), attachment.getTitle(), attachment.getInfo(), attachment.
            getSize(), attachment.getType(), "" + userId, attachment.getCreationDate(), attachment.
            getXmlForm()));
        version.setPublicDocument((versionType == DocumentVersion.TYPE_PUBLIC_VERSION));
        version.setStatus("" + DocumentVersion.STATUS_VALIDATION_NOT_REQ);
        AttachmentServiceFactory.getAttachmentService().createAttachment(version,
            content, indexIt);
      }

      if (attachment.isRemoveAfterImport()) {
        boolean removed = FileUtils.deleteQuietly(attachmentImportExport.getAttachmentFile(
            attachment));
        if (!removed) {
          SilverTrace.error("versioning", "VersioningImportExport.importDocuments()",
              "root.MSG_GEN_PARAM_VALUE", "Can't remove file " + attachmentImportExport
              .getAttachmentFile(attachment));
        }
      }
      nbFilesProcessed++;
    }
    return nbFilesProcessed;
  }

  private SimpleDocument isDocumentExist(List<SimpleDocument> documents, AttachmentDetail attachment) {
    String documentName = attachment.getTitle();
    if (!StringUtil.isDefined(documentName)) {
      documentName = attachment.getLogicalName();
    }
    for (SimpleDocument document : documents) {
      if (documentName.equalsIgnoreCase(document.getFilename())) {
        return document;
      }
    }
    return null;
  }

  public List<SimpleDocument> importDocuments(ForeignPK objectPK, List<Document> documents,
      int userId, boolean indexIt) throws RemoteException, FileNotFoundException {
    SilverTrace.info("versioning", "VersioningImportExport.importDocuments()",
        "root.GEN_PARAM_VALUE", objectPK.toString());
    boolean launchCallback = false;
    int userIdCallback = -1;

    List<SimpleDocument> importedDocs = new ArrayList<SimpleDocument>(documents.size());

    // get existing documents of object
    List<SimpleDocument> existingDocuments = AttachmentServiceFactory.getAttachmentService().
        listDocumentsByForeignKey(objectPK, null);
    FormTemplateImportExport xmlIE = null;
    for (Document document : documents) {
      SimpleDocument existingDocument = null;
      if (document.getPk() != null && StringUtil.isDefined(document.getPk().getId())
          && !"-1".equals(document.getPk().getId())) {
        existingDocument = AttachmentServiceFactory.getAttachmentService().searchDocumentById(
            new SimpleDocumentPK("", document.getPk()), null);
      }
      if (existingDocument == null) {
        existingDocument = isDocumentExist(existingDocuments, document.getName());
        if (existingDocument != null) {
          document.setPk(new DocumentPK((int) existingDocument.getPk().getOldSilverpeasId(),
              objectPK.getInstanceId()));
        }
      }

      if (existingDocument != null && existingDocument.isVersioned()) {
        List<DocumentVersion> versions = document.getVersionsType().getListVersions();
        for (DocumentVersion version : versions) {
          version.setInstanceId(objectPK.getInstanceId());
          existingDocument = addVersion(version, existingDocument, userId, indexIt);
          XMLModelContentType xmlContent = version.getXMLModelContentType();
          // Store xml content
          try {
            if (xmlContent != null) {
              if (xmlIE == null) {
                xmlIE = new FormTemplateImportExport();
              }
              ForeignPK pk = new ForeignPK(version.getPk().getId(), version.getPk().
                  getInstanceId());
              xmlIE.importXMLModelContentType(pk, "Versioning", xmlContent,
                  Integer.toString(version.getAuthorId()));
            }
          } catch (Exception e) {
            SilverTrace.error("versioning", "VersioningImportExport.importDocuments()",
                "root.MSG_GEN_PARAM_VALUE", e);
          }
        }
      } else {
        // Il n'y a pas de document portant le même nom
        // On crée un nouveau document
        List<DocumentVersion> versions = document.getVersionsType().getListVersions();
        SimpleDocument simpleDocument = null;
        for (DocumentVersion version : versions) {
          if (simpleDocument == null) {
            if (version.getCreationDate() == null) {
              version.setCreationDate(new Date());
            }
            if (version.getAuthorId() == -1) {
              version.setAuthorId(userId);
            }
            // Création du nouveau document

            XMLModelContentType xmlContent = version.getXMLModelContentType();
            String xmlFormId = null;
            if (xmlContent != null) {
              xmlFormId = xmlContent.getName();
            }
            simpleDocument = new HistorisedDocument(new SimpleDocumentPK(null, objectPK.
                getInstanceId()), objectPK.getId(), -1, new SimpleAttachment(version.
                getLogicalName(), I18NHelper.defaultLanguage,
                document.getName(), document.getDescription(), version.getSize(), version.
                getMimeType(), version.getAuthorId() + "", version.getCreationDate(), xmlFormId));

            simpleDocument.setStatus("" + DocumentVersion.STATUS_VALIDATION_NOT_REQ);
            boolean isPublic = version.getType() == DocumentVersion.TYPE_PUBLIC_VERSION;
            if (isPublic) {
              launchCallback = true;
              userIdCallback = version.getAuthorId();
            }
            simpleDocument.setPublicDocument(isPublic);
            InputStream content = getVersionContent(version);
            simpleDocument.setContentType(version.getMimeType());
            simpleDocument.setSize(version.getSize());
            simpleDocument.setFilename(version.getLogicalName());
            simpleDocument = AttachmentServiceFactory.getAttachmentService().createAttachment(
                simpleDocument, content, indexIt);
            IOUtils.closeQuietly(content);
          } else {
            simpleDocument = addVersion(version, simpleDocument, userId, indexIt);
          }
          importedDocs.add(simpleDocument);
          // Store xml content
          try {
            XMLModelContentType xmlContent = version.getXMLModelContentType();
            if (xmlContent != null) {
              if (xmlIE == null) {
                xmlIE = new FormTemplateImportExport();
              }
              ForeignPK pk = new ForeignPK(version.getPk().getId(), version.getPk().getInstanceId());
              xmlIE.importXMLModelContentType(pk, "Versioning", xmlContent, Integer.
                  toString(version.getAuthorId()));
            }
          } catch (Exception e) {
            SilverTrace.error("versioning", "VersioningImportExport.importDocuments()",
                "root.MSG_GEN_PARAM_VALUE", e);
          }
        }
      }
      if (launchCallback) {
        CallBackManager callBackManager = CallBackManager.get();
        callBackManager.invoke(CallBackManager.ACTION_VERSIONING_UPDATE, userIdCallback, objectPK.
            getInstanceId(), objectPK.getId());
      }
    }
    return importedDocs;
  }

  private SimpleDocument isDocumentExist(List<SimpleDocument> documents, String name) {
    if (name != null) {
      for (SimpleDocument document : documents) {
        if (name.equalsIgnoreCase(document.getFilename()) || name.equalsIgnoreCase(document.
            getTitle())) {
          return document;
        }
      }
    }
    return null;
  }

  protected SimpleDocument addVersion(DocumentVersion version, SimpleDocument existingDocument,
      int userId, boolean indexIt) throws FileNotFoundException {
    boolean isPublic = (version.getType() == DocumentVersion.TYPE_PUBLIC_VERSION);
    boolean launchCallback = (version.getType() == DocumentVersion.TYPE_PUBLIC_VERSION);
    existingDocument.setPublicDocument(isPublic);
    existingDocument.setStatus("" + DocumentVersion.STATUS_VALIDATION_NOT_REQ);
    existingDocument.setUpdated(new Date());
    existingDocument.setUpdatedBy("" + userId);
    XMLModelContentType xmlContent = version.getXMLModelContentType();
    if (xmlContent != null) {
      existingDocument.setXmlFormId(xmlContent.getName());
    }
    AttachmentServiceFactory.getAttachmentService().
        lock(existingDocument.getId(), "" + userId, existingDocument.getLanguage());
    AttachmentServiceFactory.getAttachmentService().updateAttachment(existingDocument,
        getVersionContent(version), indexIt, launchCallback);
    AttachmentServiceFactory.getAttachmentService().
        unlock(new UnlockContext(existingDocument.getId(), "" + userId, existingDocument.
        getLanguage()));
    return AttachmentServiceFactory.getAttachmentService().searchDocumentById(existingDocument.
        getPk(), existingDocument.getLanguage());
  }

  InputStream getVersionContent(DocumentVersion version) throws FileNotFoundException {
    File file = new File(FileUtil.convertPathToServerOS(version.getDocumentPath()));
    if (file == null || !file.exists() || !file.isFile()) {
      String baseDir = resources.getString("importRepository");
      file = new File(FileUtil.convertPathToServerOS(baseDir + File.separatorChar + version.
          getPhysicalName()));
    }
    version.setMimeType(FileUtil.getMimeType(file.getName()));
    if (!StringUtil.isDefined(version.getLogicalName())) {
      version.setLogicalName(file.getName());
    }
    version.setSize(file.length());
    return new FileInputStream(file);
  }
}

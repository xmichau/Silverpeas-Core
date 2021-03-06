/**
 * Copyright (C) 2000 - 2013 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have received a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/docs/core/legal/floss_exception.html"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.silverpeas.form.record;

import com.silverpeas.form.DataRecord;
import com.silverpeas.form.Field;
import com.silverpeas.form.FieldDisplayer;
import com.silverpeas.form.FieldTemplate;
import com.silverpeas.form.FormException;
import com.silverpeas.form.RecordSet;
import com.silverpeas.form.RecordTemplate;
import com.silverpeas.form.TypeManager;
import com.silverpeas.form.displayers.WysiwygFCKFieldDisplayer;
import com.silverpeas.util.ForeignPK;
import com.silverpeas.util.StringUtil;
import com.silverpeas.util.i18n.I18NHelper;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import org.silverpeas.attachment.AttachmentException;
import org.silverpeas.attachment.AttachmentServiceFactory;
import org.silverpeas.attachment.model.DocumentType;
import org.silverpeas.attachment.model.SimpleDocument;
import org.silverpeas.attachment.model.SimpleDocumentPK;
import org.silverpeas.search.indexEngine.model.FullIndexEntry;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The GenericRecordSet manage DataRecord built on a RecordTemplate and saved by the
 * GenericRecordSetManager.
 * @see DataRecord
 */
public class GenericRecordSet implements RecordSet, Serializable {

  private static final long serialVersionUID = 1L;
  private IdentifiedRecordTemplate recordTemplate = null;

  /**
   * The generic record set is built upon a RecordTemplate.
   */
  public GenericRecordSet(IdentifiedRecordTemplate recordTemplate) {
    this.recordTemplate = recordTemplate;
  }

  /**
   * Returns the RecordTemplate shared by all the DataRecord of this RecordSet.
   * @return the RecordTemplate shared by all the DataRecord of this RecordSet.
   */
  @Override
  public RecordTemplate getRecordTemplate() {
    return recordTemplate;
  }

  /**
   * Returns an empty DataRecord built on the RecordTemplate. This record is not yet managed by this
   * RecordSet. This is only an empty record which must be filled and saved in order to become a
   * DataRecord of this RecordSet.
   * @return an empty DataRecord.
   * @throws FormException
   */
  @Override
  public DataRecord getEmptyRecord() throws FormException {
    return recordTemplate.getEmptyRecord();
  }

  /**
   * Returns the DataRecord with the given id.
   * @param objectId
   * @return the DataRecord with the given id.
   * @throws FormException when the id is unknown.
   */
  @Override
  public DataRecord getRecord(String objectId) throws FormException {
    return getGenericRecordSetManager().getRecord(recordTemplate, objectId);
  }

  /**
   * Returns the DataRecord with the given id.
   * @param objectId
   * @param language
   * @return the DataRecord with the given id.
   * @throws FormException when the id is unknown.
   */
  @Override
  public DataRecord getRecord(String objectId, String language) throws FormException {
    if (!I18NHelper.isI18N || I18NHelper.isDefaultLanguage(language)) {
      language = null;
    }
    return getGenericRecordSetManager().getRecord(recordTemplate, objectId, language);
  }

  /**
   * Inserts the given DataRecord and set its id.
   * @throw FormException when the record doesn't have the required template.
   * @throw FormException when the record has a not null id.
   * @throw FormException when the insert fail.
   */
  private void insert(DataRecord record) throws FormException {
    recordTemplate.checkDataRecord(record);
    getGenericRecordSetManager().insertRecord(recordTemplate, record);
  }

  /**
   * Updates the given DataRecord.
   * @throw FormException when the record doesn't have the required template.
   * @throw FormException when the record has a null or unknown id.
   * @throw FormException when the update fail.
   */
  private void update(DataRecord record) throws FormException {
    recordTemplate.checkDataRecord(record);
    getGenericRecordSetManager().updateRecord(recordTemplate, record);
  }

  /**
   * Save the given DataRecord. If the record id is null then the record is inserted in this
   * RecordSet. Else the record is updated.
   * @param record
   * @throws FormException when the record doesn't have the required , when the record has an
   * unknown id, when the insert or update fail.
   */
  @Override
  public void save(DataRecord record) throws FormException {
    if (record.isNew()) {
      insert(record);
    } else {
      update(record);
    }
  }

  private void indexRecord(String recordId, String formName,
      FullIndexEntry indexEntry, String language) throws FormException {
    SilverTrace.info("form", "GenericRecordSet.index()",
        "root.MSG_GEN_ENTER_METHOD", "recordId = " + recordId + ", language = "
        + language);
    DataRecord data = getRecord(recordId, language);
    if (data != null) {
      String[] fieldNames = data.getFieldNames();
      Field field = null;
      for (String fieldName : fieldNames) {
        field = data.getField(fieldName);
        if (field != null) {
          FieldTemplate fieldTemplate = recordTemplate.getFieldTemplate(fieldName);
          if (fieldTemplate != null) {
            String fieldType = fieldTemplate.getTypeName();
            String fieldDisplayerName = fieldTemplate.getDisplayerName();
            try {
              if (!StringUtil.isDefined(fieldDisplayerName)) {
                fieldDisplayerName = TypeManager.getInstance().getDisplayerName(fieldType);
              }
              FieldDisplayer fieldDisplayer = TypeManager.getInstance().getDisplayer(
                  fieldType, fieldDisplayerName);
              if (fieldDisplayer != null) {
                String key = formName + "$$" + fieldName;
                fieldDisplayer.index(indexEntry, key, fieldName, field, language,
                    fieldTemplate.isUsedAsFacet());
              }
            } catch (FormException fe) {
              SilverTrace.error("form", "AbstractForm.update",
                  "form.EXP_UNKNOWN_FIELD", null, fe);
            } catch (Exception e) {
              SilverTrace.error("form", "AbstractForm.update",
                  "form.EXP_UNKNOWN_FIELD", null, e);
            }
          }
        }
      }
    }
  }

  @Override
  public void indexRecord(String recordId, String formName, FullIndexEntry indexEntry)
      throws FormException {
    if (!I18NHelper.isI18N) {
      indexRecord(recordId, formName, indexEntry, null);
    } else {
      List<String> languages =
          getGenericRecordSetManager().getLanguagesOfRecord(recordTemplate, recordId);
      for (String language : languages) {
        indexRecord(recordId, formName, indexEntry, language);
      }
    }
  }

  /**
   * Deletes the given DataRecord and set to null its id.
   *
   * @param record
   * @throws FormException when the record doesn't have the required template., when the record has
   * an unknown id, when the delete fail.
   */
  @Override
  public void delete(DataRecord record) throws FormException {
    if (record != null) {

      ForeignPK foreignPK = new ForeignPK(record.getId(), recordTemplate.getInstanceId());

      // remove files managed by WYSIWYG fields
      WysiwygFCKFieldDisplayer.removeContents(foreignPK);

      // remove form documents registred into JCR
      List<SimpleDocument> documents = AttachmentServiceFactory.getAttachmentService().
          listDocumentsByForeignKeyAndType(foreignPK, DocumentType.form, null);
      for (SimpleDocument doc : documents) {
        AttachmentServiceFactory.getAttachmentService().deleteAttachment(doc);
      }

      List<String> languages =
          getGenericRecordSetManager().getLanguagesOfRecord(recordTemplate, record.getId());
      for (String lang : languages) {
        DataRecord aRecord = getRecord(record.getId(), lang);
        // remove data in database
        getGenericRecordSetManager().deleteRecord(recordTemplate, aRecord);
      }
    }
  }
  
  public void delete(String objectId) throws FormException {
    DataRecord data = getRecord(objectId);
    delete(data);
  }
  
  @Override
  public void move(ForeignPK fromPK, ForeignPK toPK, RecordTemplate toRecordTemplate)
      throws FormException {
    
    // move WYSIWYG fields
    WysiwygFCKFieldDisplayer wysiwygDisplayer = new WysiwygFCKFieldDisplayer();
    try {
      wysiwygDisplayer.move(fromPK, toPK);
    } catch (IOException e) {
      SilverTrace.error("form", "GenericRecordSet.move", "form.CANT_MOVE_WYSIWYG_FIELD_CONTENT", null, e);
    }
    
    // move files, images and video of form
    List<SimpleDocument> documents = AttachmentServiceFactory.getAttachmentService().
        listDocumentsByForeignKeyAndType(fromPK, DocumentType.form, null);
    for (SimpleDocument doc : documents) {
      AttachmentServiceFactory.getAttachmentService().moveDocument(doc, toPK);
    }
    
    // update data stored in database
    List<String> languages = getGenericRecordSetManager().getLanguagesOfRecord(recordTemplate, fromPK.getId());
    for (String lang : languages) {
      GenericDataRecord record = (GenericDataRecord) getRecord(fromPK.getId(), lang);
      if (record != null) {
        // move record itself in database
        getGenericRecordSetManager().moveRecord(record.getInternalId(),
            (IdentifiedRecordTemplate) toRecordTemplate);
      }
    }
  }
  
  @Override
  public void copy(ForeignPK fromPK, ForeignPK toPK, RecordTemplate toRecordTemplate,
      Map<String, String> oldAndNewFileIds) throws FormException {
    
    // clone WYSIWYG fields content
    WysiwygFCKFieldDisplayer wysiwygDisplayer = new WysiwygFCKFieldDisplayer();
    try {
      wysiwygDisplayer.cloneContents(fromPK, toPK, oldAndNewFileIds);
    } catch (Exception e) {
      SilverTrace.error("form", "GenericRecordSet.copy", "form.EX_CLONE_FAILURE", null, e);
    }
    
    // copy files, images and videos
    Map<String, String> ids = new HashMap<String, String>();
    try {
      List<SimpleDocument> originals = AttachmentServiceFactory.getAttachmentService()
          .listDocumentsByForeignKeyAndType(fromPK, DocumentType.form, null);
      for (SimpleDocument original : originals) {
        SimpleDocumentPK clonePk =
            AttachmentServiceFactory.getAttachmentService().copyDocument(original, toPK);
        ids.put(original.getId(), clonePk.getId());
      }
    } catch (AttachmentException e) {
      throw new FormException("form", "", e);
    }
    
    // copy data stored in database
    List<String> languages = getGenericRecordSetManager().getLanguagesOfRecord(recordTemplate, fromPK.getId());
    for (String lang : languages) {
      GenericDataRecord record = (GenericDataRecord) getRecord(fromPK.getId(), lang);
      if (record != null) {
        record.setInternalId(-1);
        record.setId(toPK.getId());
        
        // replace files reference
        replaceIds(ids, record, toPK.getId());  
    
        // insert record itself in database
        getGenericRecordSetManager()
            .insertRecord((IdentifiedRecordTemplate) toRecordTemplate, record);
      }
    }
  }

  @Override
  public void clone(String originalExternalId, String originalComponentId, String cloneExternalId,
      String cloneComponentId, Map<String, String> attachmentIds) throws FormException {
    GenericDataRecord record = (GenericDataRecord) getRecord(originalExternalId);
    record.setInternalId(-1);
    record.setId(cloneExternalId);
    
    ForeignPK fromPK = new ForeignPK(originalExternalId, originalComponentId);
    ForeignPK toPK = new ForeignPK(cloneExternalId, cloneComponentId);

    // clone wysiwyg fields content
    WysiwygFCKFieldDisplayer wysiwygDisplayer = new WysiwygFCKFieldDisplayer();
    try {
      wysiwygDisplayer.cloneContents(fromPK, toPK, attachmentIds);
    } catch (Exception e) {
      SilverTrace.error("form", "AbstractForm.clone", "form.EX_CLONE_FAILURE", null, e);
    }

    // clone images and videos
    try {
      List<SimpleDocument> originals = AttachmentServiceFactory.getAttachmentService()
          .listDocumentsByForeignKeyAndType(fromPK, DocumentType.form, null);
      originals.addAll(AttachmentServiceFactory.getAttachmentService()
          .listDocumentsByForeignKeyAndType(fromPK, DocumentType.video, null));
      Map<String, String> ids = new HashMap<String, String>(originals.size());
      for (SimpleDocument original : originals) {
        SimpleDocumentPK clonePk = AttachmentServiceFactory.getAttachmentService().cloneDocument(
            original, cloneExternalId);
        ids.put(original.getId(), clonePk.getId());
      }
      replaceIds(ids, record, originalExternalId);
    } catch (AttachmentException e) {
      throw new FormException("form", "", e);
    }

    insert(record);
  }

  @Override
  public void merge(String fromExternalId, String fromComponentId, String toExternalId,
      String toComponentId, Map<String, String> attachmentIds) throws FormException {
    GenericDataRecord fromRecord = (GenericDataRecord) getRecord(fromExternalId);
    GenericDataRecord toRecord = (GenericDataRecord) getRecord(toExternalId);

    fromRecord.setInternalId(toRecord.getInternalId());
    fromRecord.setId(toExternalId);

    // merge wysiwyg fields content
    WysiwygFCKFieldDisplayer wysiwygDisplayer = new WysiwygFCKFieldDisplayer();
    try {
      wysiwygDisplayer.mergeContents(fromComponentId, fromExternalId, toComponentId, toExternalId);
    } catch (Exception e) {
      SilverTrace.error("form", "AbstractForm.clone", "form.EX_MERGE_FAILURE", null, e);
    }

    // merge images and videos
    ForeignPK fromPK = new ForeignPK(fromExternalId, fromComponentId);
    ForeignPK toPK = new ForeignPK(toExternalId, toComponentId);
    try {
      Map<String, String> ids = AttachmentServiceFactory.getAttachmentService().mergeDocuments(toPK,
          fromPK, DocumentType.form);
      Map<String, String> videoIds = AttachmentServiceFactory.getAttachmentService().mergeDocuments(
          toPK, fromPK, DocumentType.video);
      ids.putAll(videoIds);
      replaceIds(ids, fromRecord, toExternalId);
    } catch (AttachmentException e) {
      throw new FormException("form", "", e);
    }

    update(fromRecord);
  }

  private void replaceIds(Map<String, String> ids, GenericDataRecord record, String recordIdFrom)
      throws FormException {
    String[] fieldNames = record.getFieldNames();
    for (String fieldName : fieldNames) {
      Field field = record.getField(fieldName);
      if (field != null) {
        FieldTemplate fieldTemplate = recordTemplate.getFieldTemplate(fieldName);
        if (fieldTemplate != null) {
          String fieldType = fieldTemplate.getTypeName();
          String fieldDisplayerName = fieldTemplate.getDisplayerName();
          try {
            if (!StringUtil.isDefined(fieldDisplayerName)) {
              fieldDisplayerName = TypeManager.getInstance().getDisplayerName(fieldType);
            }
            if (Field.TYPE_FILE.equals(fieldType)) {
              if (ids.containsKey(field.getStringValue())) {
                field.setStringValue(ids.get(field.getStringValue()));
              }
            } else {
              String oldValue = field.getStringValue();
              if (oldValue != null && oldValue.startsWith(WysiwygFCKFieldDisplayer.dbKey)) {
                // Wysiwyg case
                String newValue = oldValue.replaceAll(recordIdFrom, record.getId());
                field.setStringValue(newValue);
              }
            }
          } catch (Exception e) {
            SilverTrace.error("form", "AbstractForm.update",
                "form.EXP_UNKNOWN_FIELD", null, e);
          }
        }
      }
    }
  }

  /**
   * Gets an instance of a GenericRecordSet objects manager.
   * @return a GenericRecordSetManager instance.
   */
  protected GenericRecordSetManager getGenericRecordSetManager() {
    return GenericRecordSetManager.getInstance();
  }

}
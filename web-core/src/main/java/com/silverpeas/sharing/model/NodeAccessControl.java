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
package com.silverpeas.sharing.model;

import java.rmi.RemoteException;
import java.util.Collection;

import javax.ejb.CreateException;

import org.apache.commons.collections.CollectionUtils;
import org.silverpeas.attachment.model.SimpleDocument;

import com.silverpeas.sharing.security.AbstractShareableAccessControl;
import com.silverpeas.util.ForeignPK;
import com.stratelia.webactiv.util.EJBUtilitaire;
import com.stratelia.webactiv.util.JNDINames;
import com.stratelia.webactiv.util.WAPrimaryKey;
import com.stratelia.webactiv.util.node.control.NodeBm;
import com.stratelia.webactiv.util.node.model.NodeDetail;
import com.stratelia.webactiv.util.node.model.NodePK;
import com.stratelia.webactiv.util.publication.control.PublicationBm;
import com.stratelia.webactiv.util.publication.model.Alias;
import com.stratelia.webactiv.util.publication.model.PublicationPK;

/**
 * Access control to shared nodes and their content.
 */
public class NodeAccessControl<R> extends AbstractShareableAccessControl<NodeTicket, R> {

  private PublicationBm publicationBm;
  private NodeBm nodeBm;

  NodeAccessControl() {
    super();
  }

  @Override
  protected boolean isReadable(NodeTicket ticket, R accessedObject) throws Exception {
    NodePK nodePk = new NodePK(String.valueOf(ticket.getSharedObjectId()), ticket.getComponentId());
    Collection<NodePK> autorizedNodes = getNodeDescendants(nodePk);
    autorizedNodes.add(nodePk);
    if (accessedObject instanceof SimpleDocument) {
      SimpleDocument attachment = (SimpleDocument) accessedObject;
      return isPublicationReadable(new ForeignPK(attachment.
          getForeignId(), attachment.getInstanceId()), nodePk.getInstanceId(), autorizedNodes);
    }
    if (accessedObject instanceof NodeDetail) {
      NodeDetail node = (NodeDetail) accessedObject;
      return autorizedNodes.contains(node.getNodePK());
    }
    return false;
  }

  protected Collection<NodePK> getPublicationFathers(WAPrimaryKey pk)
      throws CreateException, RemoteException {
    return findPublicationBm().getAllFatherPK(new PublicationPK(pk.getId(), pk.getInstanceId()));
  }

  protected Collection<Alias> getPublicationAliases(WAPrimaryKey pk)
      throws CreateException, RemoteException {
    return findPublicationBm().getAlias(new PublicationPK(pk.getId(), pk.getInstanceId()));
  }

  protected Collection<NodePK> getNodeDescendants(NodePK pk)
      throws CreateException, RemoteException {
    return findNodeBm().getDescendantPKs(pk);
  }

  private boolean isPublicationReadable(WAPrimaryKey pk, String instanceId,
      Collection<NodePK> autorizedNodes) throws RemoteException, CreateException {
    if (pk.getInstanceId().equals(instanceId)) {
      Collection<NodePK> fathers = getPublicationFathers(pk);
      return CollectionUtils.containsAny(autorizedNodes, fathers);
    } else {
      // special case of an alias between two ECM applications
      // check if publication which contains attachment is an alias into this node
      Collection<Alias> aliases = getPublicationAliases(pk);
      for (Alias alias : aliases) {
        NodePK aliasPK = new NodePK(alias.getId(), alias.getInstanceId());
        if (autorizedNodes.contains(aliasPK)) {
          return true;
        }
      }
    }
    return false;
  }

  private PublicationBm findPublicationBm() {
    if (publicationBm == null) {
      publicationBm =
          EJBUtilitaire.getEJBObjectRef(JNDINames.PUBLICATIONBM_EJBHOME, PublicationBm.class);
    }
    return publicationBm;
  }

  private NodeBm findNodeBm() {
    if (nodeBm == null) {
      nodeBm = EJBUtilitaire.getEJBObjectRef(JNDINames.NODEBM_EJBHOME, NodeBm.class);
    }
    return nodeBm;
  }
}

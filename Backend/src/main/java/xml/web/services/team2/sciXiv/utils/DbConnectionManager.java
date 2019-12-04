package xml.web.services.team2.sciXiv.utils;

import org.exist.xmldb.EXistResource;
import org.springframework.stereotype.Component;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import xml.web.services.team2.sciXiv.utils.AuthenticationUtilities.ConnectionProperties;

import javax.xml.transform.OutputKeys;

@Component
public class DbConnectionManager {

	private ConnectionProperties conn;

	private String dbUri;

	public DbConnectionManager() throws Exception {
		super();
		initialize();
	}
	
	private void initialize() throws Exception {
		dbUri = "/db/sciXiv/";
		conn = AuthenticationUtilities.loadProperties();
		Class<?> cl = Class.forName(conn.getDriver());

		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");

		DatabaseManager.registerDatabase(database);
	}
	
	public void store(String collectionName, String documentId, String xmlEntity) {
		Collection col = null;
		XMLResource res = null;

		try {
			col = getOrCreateCollection(dbUri + collectionName, 0);
			res = (XMLResource) col.createResource(documentId, XMLResource.RESOURCE_TYPE);
			res.setContent(xmlEntity);
			col.storeResource(res);
		}
		catch(XMLDBException ex) {
			ex.printStackTrace();
		}
		finally {
			if (res != null) {
				try {
					((EXistResource) res).freeResources();
				} catch (XMLDBException xe) {
					xe.printStackTrace();
				}
			}
			if (col != null) {
				try {
					col.close();
				} catch (XMLDBException xe) {
					xe.printStackTrace();
				}
			}
		}
	}

	public void load(String collectionName, String documentId) {
		Collection col = null;
		XMLResource res = null;

		try {
			col = DatabaseManager.getCollection(conn.getUri() + collectionName);
			col.setProperty(OutputKeys.INDENT, "yes");
			res = (XMLResource)col.getResource(documentId);

			if(res == null)
				throw new Exception();
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
		finally{
			if(res != null) {
				try {
					((EXistResource)res).freeResources();
				} catch (XMLDBException xe) {
					xe.printStackTrace();
				}
			}

			if(col != null) {
				try {
					col.close();
				} catch (XMLDBException xe) {
					xe.printStackTrace();
				}
			}
		}
	}

	public Collection getOrCreateCollection(String collectionUri, int pathSegmentOffset) throws XMLDBException {
		Collection col = DatabaseManager.getCollection(conn.getUri() + collectionUri, conn.getUser(), conn.getPassword());

		if (col == null) {

			if (collectionUri.startsWith("/")) {
				collectionUri = collectionUri.substring(1);
			}

			String[] pathSegments = collectionUri.split("/");

			if (pathSegments.length > 0) {
				StringBuilder path = new StringBuilder();

				for (int i = 0; i <= pathSegmentOffset; i++) {
					path.append("/").append(pathSegments[i]);
				}

				Collection startCol = DatabaseManager.getCollection(conn.getUri() + path, conn.getUser(), conn.getPassword());

				if (startCol == null) {
					String parentPath = path.substring(0, path.lastIndexOf("/"));
					Collection parentCol = DatabaseManager.getCollection(conn.getUri() + parentPath, conn.getUser(),
							conn.getPassword());

					CollectionManagementService mgt = (CollectionManagementService) parentCol
							.getService("CollectionManagementService", "1.0");

					col = mgt.createCollection(pathSegments[pathSegmentOffset]);

					col.close();
					parentCol.close();

				} else {
					startCol.close();
				}
			}
			return getOrCreateCollection(collectionUri, ++pathSegmentOffset);
		} else {
			return col;
		}
	}
}

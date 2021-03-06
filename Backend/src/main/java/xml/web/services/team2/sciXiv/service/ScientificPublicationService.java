package xml.web.services.team2.sciXiv.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;
import xml.web.services.team2.sciXiv.dto.SciPubDTO;
import xml.web.services.team2.sciXiv.dto.SearchPublicationsDTO;
import xml.web.services.team2.sciXiv.exception.*;
import xml.web.services.team2.sciXiv.model.TUser;
import xml.web.services.team2.sciXiv.model.businessProcess.TProcessStateEnum;
import xml.web.services.team2.sciXiv.repository.ScientificPublicationRepository;
import xml.web.services.team2.sciXiv.repository.UserRepository;
import xml.web.services.team2.sciXiv.utils.database.SparqlUtil;
import xml.web.services.team2.sciXiv.utils.dom.DOMParser;
import xml.web.services.team2.sciXiv.utils.xslt.DOMToXMLTransformer;
import xml.web.services.team2.sciXiv.utils.xslt.MetadataExtractor;
import xml.web.services.team2.sciXiv.utils.xslt.XSLFOTransformer;
import xml.web.services.team2.sciXiv.utils.xslt.XSLTranspiler;

import javax.mail.MessagingException;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

@Service
public class ScientificPublicationService {

	private static String schemaPath = "src/main/resources/static/xmlSchemas/scientificPublication.xsd";

	private static String xslPath = "src/main/resources/static/xsl/scientificPublicationToHTML.xsl";

	private static String xslForAnonymusPublicatonPath = "src/main/resources/static/xsl/publicationAnonymusToHTML.xsl";

	private static String xslFOPath = "src/main/resources/static/xsl/xsl-fo/scientificPublicationToPDF.xsl";

	@Autowired
	ScientificPublicationRepository scientificPublicationRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	BusinessProcessService businessProcessService;

	@Autowired
	NotificationService notificationService;

	@Autowired
	DOMParser domParser;

	@Autowired
	MetadataExtractor metadataExtractor;

	@Autowired
	DOMToXMLTransformer transformer;

	@Autowired
	XSLFOTransformer xslTransformer;

	@Autowired
	XSLTranspiler xslTranspiler;

	public String findByNameAndVersion(String name, int version) throws XMLDBException, DocumentLoadingFailedException {
		return scientificPublicationRepository.findByNameAndVersion(name, version);
	}

	public String save(String sciPub) throws ParserConfigurationException, DocumentParsingFailedException, SAXException,
			IOException, DocumentStoringFailedException, TransformerException, UserRetrievingFailedException, UserSavingFailedException {
		Document document = domParser.buildAndValidateDocument(sciPub, schemaPath);
		NodeList nodeList = document.getElementsByTagName("sp:title");
		String title = nodeList.item(0).getTextContent();
		String titleOrig = title;

		try {
			businessProcessService.createBusinessProcess(titleOrig);
		} catch (Exception e) {
			e.printStackTrace();
		}

		String aboutAttr = document.getDocumentElement().getAttribute("about");
		int lastIndex = aboutAttr.lastIndexOf('/');
		String about = aboutAttr.substring(0, lastIndex + 1) + URLEncoder.encode(aboutAttr.substring(lastIndex + 1), "UTF-8");
		document.getDocumentElement().setAttribute("about", about);

		title = title.replace(" ", "");
		String name = title + "-v1";

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String dateReceived = sdf.format(new Date());

		NodeList authors = document.getElementsByTagName("sp:authors");
		NodeList keywords = document.getElementsByTagName("sp:keywords");

		Element dateReceivedElem = document.createElement("sp:dateReceived");
		dateReceivedElem.setTextContent(dateReceived);
		dateReceivedElem.setAttribute("property", "pred:dateCreated");
		NodeList metadataElem = document.getElementsByTagName("sp:metadata");
		metadataElem.item(0).insertBefore(dateReceivedElem, authors.item(0));

		Element status = document.createElement("sp:status");
		status.setTextContent("in process");
		status.setAttribute("property", "pred:creativeWorkStatus");
		metadataElem.item(0).insertBefore(status, keywords.item(0));

        NodeList references = document.getElementsByTagName("sp:reference");
        for (int i = 0; i < references.getLength(); i++) {
        	aboutAttr = references.item(i).getAttributes().getNamedItem("href").getTextContent();
			lastIndex = aboutAttr.lastIndexOf('/');
			about = aboutAttr.substring(0, lastIndex + 1) + URLEncoder.encode(aboutAttr.substring(lastIndex + 1), "UTF-8");
            references.item(i).getAttributes().getNamedItem("href").setTextContent(about);
        }

		sciPub = transformer.toXML(document);
		ByteArrayOutputStream metadataStream = new ByteArrayOutputStream();

		metadataExtractor.extractMetadata(new ByteArrayInputStream(sciPub.getBytes()), metadataStream);
		String metadata = new String(metadataStream.toByteArray());

		User current = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String email = current.getUsername();
		TUser user = userRepository.getByEmail(email);
		String publicationTitle = URLEncoder.encode(titleOrig, "UTF-8");
		user.getOwnPublications().getPublicationID().add(publicationTitle);
		userRepository.save(user);

		scientificPublicationRepository.saveMetadata(metadata);

		String ret = scientificPublicationRepository.save(sciPub, title, name);

		try {
			notifySubmissionToEditor(titleOrig, user);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ret;
	}

	public String revise(String sciPub) throws ParserConfigurationException, DocumentParsingFailedException,
			SAXException, IOException, XMLDBException, DocumentStoringFailedException {
		Document document = domParser.buildAndValidateDocument(sciPub, schemaPath);
		NodeList nodeList = document.getElementsByTagName("sp:title");
		String title = nodeList.item(0).getTextContent();
		String titleOrig = title;
		title = title.replace(" ", "");
		int lastVersion = scientificPublicationRepository.getLastVersionNumber(title);
		lastVersion++;
		String name = title + "-v" + lastVersion;

		try {
			TProcessStateEnum currentState = businessProcessService.getProcessState(titleOrig);
			if (currentState != TProcessStateEnum.ON_REVISION) {
				throw new ChangeProcessStateException(String.format(
						"Can not revise scientific publication unless it is in state ON_REVISION. Current state is: %s",
						currentState.toString()));
			}
		} catch (JAXBException e1) {
			e1.printStackTrace();
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String dateRevised = sdf.format(new Date());

		NodeList authors = document.getElementsByTagName("sp:authors");

		Element dateRevisedElem = document.createElement("sp:dateRevised");
		dateRevisedElem.setTextContent(dateRevised);
		dateRevisedElem.setAttribute("property", "pred:dateModified");
		NodeList metadataElem = document.getElementsByTagName("sp:metadata");
		metadataElem.item(0).insertBefore(dateRevisedElem, authors.item(0));

		sciPub = transformer.toXML(document);

		String resourceName = document.getDocumentElement().getAttribute("about");

		scientificPublicationRepository.insertMetadata(resourceName, "dateModified", dateRevised);

		String ret = scientificPublicationRepository.save(sciPub, title, name);

		try {
			businessProcessService.changeProcessState(titleOrig, TProcessStateEnum.REVISED);
			User userDetails = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			String revisorEmail = userDetails.getUsername();
			TUser revisor = userRepository.getByEmail(revisorEmail);
			notifySubmissionToEditor(titleOrig, revisor);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ret;
	}

	public String withdraw(String title)
			throws XMLDBException, DocumentLoadingFailedException, DocumentStoringFailedException {
		scientificPublicationRepository.withdraw(title.replace(" ", ""));
		return "Publication successfully withdrawn";
	}

	public int getLastVersionNumber(String title) throws XMLDBException {
		return scientificPublicationRepository.getLastVersionNumber(title);
	}

	public ArrayList<SciPubDTO> basicSearch(String parameter) throws XMLDBException, UnsupportedEncodingException {
		TUser user;
		try {
			User current = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			String email = current.getUsername();
			user = userRepository.getByEmail(email);
		}
		catch(Exception e) {
			user = null;
		}
		return scientificPublicationRepository.basicSearch(parameter, user);
	}

	public ArrayList<SciPubDTO> advancedSearch(SearchPublicationsDTO searchParameters) {
		String query = "SELECT * FROM <%s>\n" + "WHERE { \n" + "\t?sciPub";
		query = makeSparqlQuery(query, searchParameters);
		TUser user = null;
		try {
			User current = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			String email = current.getUsername();
			user = userRepository.getByEmail(email);
			ArrayList<String> userDocuments = (ArrayList<String>) user.getOwnPublications().getPublicationID();
			String resourceURL = "http://ftn.uns.ac.rs/scientificPublication/";
			StringBuilder titles = new StringBuilder("(");
			for (String title : userDocuments) {
				titles.append("<").append(resourceURL).append(title).append(">").append(", ");
			}
			titles.delete(titles.length() - 2, titles.length()).append(")");

			query += "\tFILTER (?sciPub IN " + titles + " || ?status = \"accepted\")\n}";
		}
		catch(Exception e) {
			query += "\tFILTER (?status = \"accepted\")\n}";
		}

		return scientificPublicationRepository.advancedSearch(query, user);
	}

	public ArrayList<SciPubDTO> getReferences(String title) {
		return scientificPublicationRepository.getReferences(title);
	}

	public String getScientificPublicationAsXHTML(String title)
			throws XMLDBException, DocumentLoadingFailedException, TransformerException {
		int lastVersion = scientificPublicationRepository.getLastVersionNumber(title.replace(" ", ""));
		String xmlDocument = scientificPublicationRepository.findByNameAndVersion(title, lastVersion);

		return xslTranspiler.generateHTML(xmlDocument, xslPath);
	}

	public String getAnonymusScientificPublicationAsXHTML(String title, int version)
			throws XMLDBException, DocumentLoadingFailedException, TransformerException {
		String xmlDocument = scientificPublicationRepository.findByNameAndVersion(title, version);

		return xslTranspiler.generateHTML(xmlDocument, xslForAnonymusPublicatonPath);
	}

	public SearchPublicationsDTO getPublicationsMetadata(String title) {
		return scientificPublicationRepository.getPublicationsMetadata(title);
	}

	private String makeSparqlQuery(String query, SearchPublicationsDTO parameters) {
		String sciPub = "";
		String literalType = "^^<http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral>";

		if (!parameters.getTitle().equals("")) {
			query += " <https://schema.org/headline> " + "\"" + parameters.getTitle() + "\"" + literalType + " ;\n";
		}
		if (!parameters.getDateReceived().equals("")) {
			query += " <https://schema.org/dateCreated> " + "\"" + parameters.getDateReceived() + "\"" + literalType
					+ " ;\n";
		}
		if (!parameters.getDateRevised().equals("")) {
			query += " <https://schema.org/dateModified> " + "\"" + parameters.getDateRevised() + "\"" + literalType
					+ " ;\n";
		}
		if (!parameters.getDateAccepted().equals("")) {
			query += " <https://schema.org/datePublished> " + "\"" + parameters.getDateAccepted() + "\"" + literalType
					+ " ;\n";
		}
		if (!parameters.getKeyword().equals("")) {
			query += " <https://schema.org/keywords> " + "\"" + parameters.getKeyword() + "\"" + literalType + " ;\n";
		}
		if (!parameters.getAuthorName().equals("")) {
			query += " <https://schema.org/author> ?author .\n" + "\t?author <https://schema.org/name> " + "\""
					+ parameters.getAuthorName() + "\"" + literalType + " .\n";
			sciPub = "?sciPub";
		}
		if (!parameters.getAuthorAffiliation().equals("")) {
			query += sciPub + " <https://schema.org/author> ?author .\n" + "\t?author <https://schema.org/affiliation> "
					+ "\"" + parameters.getAuthorAffiliation() + "\"" + literalType + " .\n";
			sciPub = "?sciPub";
		}

		query += sciPub + " <https://schema.org/creativeWorkStatus> ?status .\n";

		return query;
	}

	public Resource exportScientificPublicationAsXHTML(String title)
			throws XMLDBException, DocumentLoadingFailedException, TransformerException, IOException {
		String sciPubHTML = getScientificPublicationAsXHTML(title);

		Path file = Paths.get(title + ".html");
		Files.write(file, sciPubHTML.getBytes(StandardCharsets.UTF_8));

		return new UrlResource(file.toUri());
	}

	public Resource exportScientificPublicationAsPDF(String title) throws Exception {
		int lastVersion = scientificPublicationRepository.getLastVersionNumber(title.replace(" ", ""));
		String xmlDocument = scientificPublicationRepository.findByNameAndVersion(title, lastVersion);

		ByteArrayOutputStream outputStream = xslTranspiler.generatePDf(xmlDocument, xslFOPath);

		Path file = Paths.get(title + ".pdf");
		Files.write(file, outputStream.toByteArray());

		return new UrlResource(file.toUri());
	}

	public Resource getPublicationsMetadataAsRDF(String title)
			throws XMLDBException, DocumentLoadingFailedException, TransformerException, IOException {
		int lastVersion = scientificPublicationRepository.getLastVersionNumber(title.replace(" ", ""));
		String xmlDocument = scientificPublicationRepository.findByNameAndVersion(title, lastVersion);
		ByteArrayOutputStream metadataStream = new ByteArrayOutputStream();

		metadataExtractor.extractMetadata(new ByteArrayInputStream(xmlDocument.getBytes()), metadataStream);
		String metadata = new String(metadataStream.toByteArray());

		Model model = ModelFactory.createDefaultModel();
		model.read(new ByteArrayInputStream(metadata.getBytes()), null);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		model.write(out, SparqlUtil.RDF_XML);

		Path file = Paths.get(title + "-metadata.rdf");
		Files.write(file, out.toByteArray());

		return new UrlResource(file.toUri());
	}

	public Resource getPublicationsMetadataAsJSON(String title)
			throws XMLDBException, DocumentLoadingFailedException, IOException {
		int lastVersion = scientificPublicationRepository.getLastVersionNumber(title.replace(" ", ""));
		String xmlDocument = scientificPublicationRepository.findByNameAndVersion(title, lastVersion);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("content", xmlDocument);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		RestTemplate restTemplate = new RestTemplate();
		String url = "http://rdf-translator.appspot.com/convert/rdfa/json-ld/content";

		String content = restTemplate.postForEntity(url, request, String.class).getBody();

		Path file = Paths.get(title + "-metadata.json");
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));

		return new UrlResource(file.toUri());
	}

	private void notifySubmissionToEditor(String publicationTitle, TUser author)
			throws IOException, SAXException, ParserConfigurationException, TransformerException, MessagingException {
		String notificationContent = String.format(
				"User %s %s has submitted a new version of scientific publication: %s.", author.getFirstName(),
				author.getLastName(), publicationTitle);

		TUser reciever = userRepository.getEditor();
		String[] emails = new String[] { reciever.getEmail() };
		notificationService.notificationSendRequest(emails, notificationContent, publicationTitle, author, reciever);
	}
}

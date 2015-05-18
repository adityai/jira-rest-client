
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.2')


import groovy.json.JsonBuilder
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.apache.log4j.Logger

/**
 * Contains methods to operate with the basic JIRA objects
 */
public class JiraClient {

	final static Logger log = Logger.getLogger(JiraClient.class.getName())

	String url
	String user
	String password
	private String auth

	JiraClient(user, password, url) {
		this.user = user
		this.password = password
		this.url = url
		this.auth = "${user}:${password}".getBytes().encodeBase64().toString()
	}

	/**
	 * Retrieves JIRA issues using some JQL query
	 * @param jqlQuery
	 * JQL(JIRA Query Language) query (ex. 'project = SUPPORT status = "Open"')
	 * @return
	 * response as a JSON
	 */
	public def retrieveIssues(String jqlQuery) throws Exception {
		def path = "/rest/api/2/search/"
		def json = new JsonBuilder()
		json { jql jqlQuery }
		query(path, 'post', json)
	}

	/**
	 * Updates certain JIRA issue using JSON request
	 * @param issueKey
	 * JIRA issue key (e.g. "SUPPORT-111")
	 * @param jsonRequest
	 * JSON object (use suggestions from JIRA API docs)
	 * @return
	 * response as a JSON
	 */
	public def updateIssue(String issueKey, JsonBuilder json) throws Exception {
		def path = "/rest/api/2/issue/${issueKey}"
		query(path, 'put', json)
	}

	/**
	 * Performs transition of the certain JIRA issue using JSON request (common method)
	 * @param json
	 * @param issueKey
	 * @return
	 * @throws Exception
	 */
	public def transitionIssue(JsonBuilder json, String issueKey) throws Exception {
		def path = "/rest/api/2/issue/${issueKey}/transitions"
		query(path, 'post', json)
	}

	/**
	 * Performs transition of the certain JIRA issue using JSON request
	 * @param issueKey
	 * JIRA issue key (e.g. "SUPPORT-111")
	 * @param issueComment
	 * Issue comment itself
	 * @param label
	 * label that should be added to issue field
	 * @param issueResolution
	 * resolution according to accepted transition scheme
	 * @return
	 * response as a JSON (success or error)
	 */
	public def transitionIssue(Map params) throws Exception {
		def json = new JsonBuilder()
		json {
			if (params.issueComment) {
				update {
					comment([{
						         add { body params.issueComment }
					         }])
				}
			}
			fields {
				if (params.assigneeUser) {
					assignee { name params.assigneeUser }
				}
				if (params.issueResolution) {
					resolution { name params.issueResolution }
				}
			}
			transition { id params.transitionId }
		}
		transitionIssue(json, params.issueKey)
	}

	/**
	 * Links JIRA issues (common method)
	 * @param json
	 * @return
	 */
	public def linkIssues(JsonBuilder json) throws Exception {
		def path = "/rest/api/2/issueLink"
		query(path, 'post', json)
	}

	/**
	 * Links JIRA issues
	 * @param inwardIssueKey
	 * @param outwardIssueKey
	 * @param linkName
	 * @return
	 */
	public def linkIssues(String inwardIssueKey, String outwardIssueKey, String linkName) throws Exception {
		def json = new JsonBuilder()
		json {
			type {
				name linkName
			}
			inwardIssue {
				key inwardIssueKey
			}
			outwardIssue {
				key outwardIssueKey
			}
		}
		linkIssues(json)
	}

	/**
	 * Deletes certain JIRA issue using JSON request
	 * @param issueKey
	 * JIRA isuue key (e.g. "SUPPORT-111")
	 * @param jsonRequest
	 * JSON object (use suggestions from JIRA API docs)
	 * @return
	 * response as a JSON
	 */
	public def deleteIssue(String issueKey, JsonBuilder json) throws Exception {
		def path = "/rest/api/2/issue/${issueKey}"
		query(path, 'post', json)
	}

	/**
	 * Creates a new JIRA issue (common method)
	 * @param json
	 * @return
	 */
	public def createIssue(JsonBuilder json) throws Exception {
		def path = "/rest/api/2/issue/"
		query(path, 'post', json)
	}

	/**
	 * Creates a new JIRA issue (specific method)
	 * @param projectKey
	 * @param issueSummary
	 * @param issueDescription
	 * @param issueType
	 * @param labels
	 * String array of labels
	 * @param issueCustomField
	 * JIRA customfield_{fieldId}* @param issueCustomFieldValue
	 * value of certain custom field to be set
	 * @param assigneeUser
	 * @return
	 * response as a JSON
	 */
	public def createIssue(Map params) throws Exception {
		def json = new JsonBuilder()
		json {
			fields {
				project {
					key params.projectKey
				}
				summary params.issueSummary
				description params.issueDescription
				issuetype {
					name params.issueType
				}
				environment params.environment

				"${params.issueCustomField}"([
						value: "${params.issueCustomFieldValue}"
				])
				labels params.labels
				assignee
						{
							name params.issueAssigneeUser
						}
			}
		}
		createIssue(json).key
	}

	/**
	 * Comments certain JIRA issue using JSON request (common method)
	 * @param json
	 * @param issueKey
	 * @return
	 * @throws Exception
	 */
	public def commentIssue(JsonBuilder json, String issueKey) throws Exception {
		def path = "/rest/api/2/issue/${issueKey}/comment/"
		query(path, 'post', json)
	}

	/**
	 * Comments certain JIRA issue using JSON request
	 * @param issueKey
	 * JIRA isuue key (e.g. "SUPPORT-111")
	 * @param jsonRequest
	 * JSON object (use suggestions from JIRA API docs)
	 * @return
	 * response as a JSON
	 */
	public def commentIssue(String issueKey, String comment) throws Exception {
		def json = new JsonBuilder()
		json {
			body "${comment}"
		}
		commentIssue(json, issueKey)
	}

	/**
	 * Makes a Http  query to JIRA
	 * @param path
	 * JIRA remote API path (more information can be found in JIRA API references)
	 * @param jsonBuilder
	 * JSON object (use suggestions from JIRA API docs)
	 * @return
	 * response as a JSON (value of a key 'data')
	 */
	public def query(String path, String queryType, JsonBuilder jsonBuilder) throws Exception {
		def restClient = new RESTClient(url);
		def response;
		try {
			response = restClient."${queryType.toLowerCase()}"(contentType: ContentType.JSON,
					requestContentType: ContentType.JSON,
					path: path,
					headers: ['Authorization': "Basic ${auth}"],
					body: jsonBuilder.toString()
			)
			response.data
		} catch (Exception e) {
			log.error("${e.getMessage()} : response: ${response} ")
			throw e;
		}
	}
}


package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns={"/exercises"})
public class Exercises extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		response.setContentType("application/json");

		JsonObject responseJson = new JsonObject();
		
		try {
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "InstructorPage":
				response.setContentType("text/html");
				out.println(instructorPage(request));
				break;
			case "SelectTopics":
				response.setContentType("text/html");
				out.println(viewTopicSelectForm(request));
				break;
			case "ReviewScores":
				response.setContentType("text/html");
				out.println(reviewScores(request));
				break;
			default:
				response.setContentType("application/json");
				String authHeader = request.getHeader("Authorization");
				if (authHeader == null) throw new Exception("Unauthorized. You must launch this app from the link inside your LMS.");
				String token = authHeader.substring(7);
				String sig = Util.isValid(token);
				responseJson.addProperty("token", Util.getToken(sig));

				User user = User.getUser(sig);
				JsonObject q = getCurrentQuestion(user);
				if (q == null) throw new Exception("Unable to get a new question.");
				responseJson.add("question",q);
				out.println(responseJson.toString());
			}
		} catch (Exception e) {
			JsonObject question = new JsonObject();
			question.addProperty("type", "true_false");
			question.addProperty("id", "1");
			question.addProperty("prompt", e.getMessage());
			responseJson.add("question", question);
			out.println(responseJson.toString());	
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		try {
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "AssignTopics":
				response.setContentType("text/html");
				out.println(assignTopics(request));
				break;
			default:
				out.println(getResponseJson(request).toString());
			}
		} catch (Exception e) {
			JsonObject err = new JsonObject();
			err.addProperty("error", e.getMessage()); // + debug.toString());
			out.println(err);
		}
	}
	
	String assignTopics(HttpServletRequest request) throws Exception {
		String sig = request.getParameter("sig");
		User user = User.getUser(sig);
		if (!user.isInstructor()) throw new Exception("Unauthorized");
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		String[] topicIds = request.getParameterValues("TopicId");
		if (topicIds != null) {
			a.topicIds = new ArrayList<Long>();
			for (String tId : topicIds) a.topicIds.add(Long.parseLong(tId));
			ofy().save().entity(a).now();
		}
		return instructorPage(user,a);
	}

	JsonObject getCurrentQuestion(User user) throws Exception {
		Score s = getScore(user);
		Question q = ofy().load().type(Question.class).id(s.currentQuestionId).safe();
		JsonObject j = new JsonObject();
	
		String prompt = q.prompt;
		if (q.requiresParser()) {
			Integer parameter = new Random().nextInt();
			q.setParameters(parameter);
			j.addProperty("parameter",parameter);
			prompt = q.parseString(q.prompt);
		}
		j.addProperty("id", q.id);
		j.addProperty("type", q.type);
		j.addProperty("prompt", prompt);
	
		if (q.units != null) j.addProperty("units", q.units);
		if (q.choices != null) {
			j.addProperty("scrambled", q.scrambleChoices);
			JsonArray choices = new JsonArray();
			for (String c : q.choices) choices.add(c);
			j.add("choices", choices);
		}
		return j;
	}
	
	JsonObject getResponseJson(HttpServletRequest request) throws Exception {
		String authHeader = request.getHeader("Authorization");
		if (authHeader == null) throw new Exception("Unauthorized. You must launch this app from the link inside your LMS.");
		String token = authHeader.substring(7);
		String sig = Util.isValid(token);
		
		BufferedReader reader = request.getReader();
		JsonObject requestJson = JsonParser.parseReader(reader).getAsJsonObject();
		
		Long questionId = requestJson.get("id").getAsLong();
		String studentAnswer = requestJson.get("answer").getAsString();
		
		Question q = ofy().load().type(Question.class).id(questionId).safe();
		if (q.requiresParser()) {
			Integer parameter = requestJson.get("parameter").getAsInt();
			q.setParameters(parameter);
		}
		
		User user = User.getUser(sig);
		Score s = getScore(user);
		boolean correct = q.isCorrect(studentAnswer);
		s.update(user, q, correct?1:0);
		
		StringBuffer buf = new StringBuffer();
		buf.append(correct?"<h2>That's right! Your answer is correct.</h2>":
			"<h2>Sorry, your answer is not correct</h2>The correct answer is " 
				+ q.getCorrectAnswer()
				+ (q.units == null?"":" " + q.units)
				+ "<br/>"
				+ "Your score on this assignment is " + s.totalScore + "%");
		
		JsonObject responseJson = new JsonObject();
		responseJson.addProperty("token",Util.getToken(sig));
		responseJson.addProperty("html", buf.toString());
		return responseJson;
	}

	Score getScore(User user) throws Exception {
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		return getScore(user,a);
	}
	
	Score getScore(User user, Assignment a) throws Exception {
		Score s = null;
		Key<User> userKey = key(User.class, user.hashedId);
		Key<Score> scoreKey = key(userKey, Score.class, a.id);
		try {
			s = ofy().load().key(scoreKey).safe();
			if (!s.topicIds.equals(a.topicIds)) s.repairMe(a); 
		} catch (Exception e) {
			s = new Score(user.hashedId,a);
			ofy().save().entity(s).now();
		}
		return s;
	}
	
	String instructorPage(HttpServletRequest request) throws Exception {
		User user = User.getUser(request.getParameter("sig"));
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		return instructorPage(user,a);
	}
	
	static String instructorPage(User user, Assignment a) throws Exception {
		if (!user.isInstructor()) throw new Exception("Unauthorized");
		
		StringBuffer buf = new StringBuffer(Util.head("Instructor Page"));
		
		buf.append(Util.banner + "<h1>Exercises - Instructor Page</h1>"
				+ "This is a formative, adaptive assignment that will help your students "
				+ "prepare for the AP Chemistry Exam. It is a series of questions and numeric "
				+ "problems drawn from the topics below that gradually increase in difficulty."
				+ "<ul>"
				+ "<li>Formative - students may work as many problems as necessary to "
				+ "achieve a score of 100%. The correct answer is provided after each "
				+ "submission, allowing students to learn as they work.</li>"
				+ "<li>Adaptive - the assignment provides a personalized learning experience "
				+ "by tailoring the questions to each student's needs based on their prior "
				+ "responses.</li>"
				+ "</ul>"
				+ "<h2>Topics Covered</h2>"
				+ "This assignment covers the following "
				+ "<a href=https://apcentral.collegeboard.org/courses/ap-chemistry target=_blank>"
				+ "AP Chemistry</a> topics:");
		
		Map<Long,APChemTopic> topics = ofy().load().type(APChemTopic.class).ids(a.topicIds);
		buf.append("<ul>");
		for (Long tId : a.topicIds) buf.append("<li>" + topics.get(tId).title + "</li>");
		buf.append("</ul>"
				+ "You may "
				+ "<a href=/exercises?UserRequest=SelectTopics&sig=" + user.getTokenSignature() + ">"
				+ "add or delete topics</a> to suit the current needs of your class.<p>");
		
		buf.append("<a href=/exercises?UserRequest=ReviewScores&sig=" + user.getTokenSignature()
				+ ">Review your students' scores on this assignment</a><p>");
		
		buf.append("<a class='btn btn-primary' href='/exercises/index.html?t=" + Util.getToken(user.getTokenSignature()) + "'>"
				+ "View This Assignment</a><p>");
		
		return buf.toString() + Util.foot();
	}
	
	String viewTopicSelectForm(HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer(Util.head("Select Topics"));
		buf.append(Util.banner + "<h1>Select Topics for This Assignment</h1>");
		
		User user = User.getUser(request.getParameter("sig"));
		if (!user.isInstructor()) return null;
		
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		
		buf.append("<form method=post action=/exercises>"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
				+ "<input type=hidden name=UserRequest value=AssignTopics />"
				+ "<input type=submit class='btn btn-primary' value='Click here to assign the topics selected below.' /> "
				+ "<a class='btn btn-primary' href=/exercises?UserRequest=InstructorPage&sig=" + user.getTokenSignature() + ">Quit</a><br/><br/>"
				+ "<ul style='list-style: none;'>");
		
		List<APChemUnit> units = ofy().load().type(APChemUnit.class).order("unitNumber").list();
		for (APChemUnit u : units) {
			buf.append("<li><label><input type=checkbox class=unitCheckbox id=" + u.id 
					+ " value=" + u.id + " onclick=unitClicked('" + u.id + "');"
					+ " /> <b>Unit " + u.unitNumber + " - " + u.title + "</b></label></li>");
			buf.append("<ul style='list-style: none;'>");
			List<APChemTopic> topics = ofy().load().type(APChemTopic.class).filter("unitId",u.id).order("topicNumber").list();
			for(APChemTopic t : topics) {
				buf.append("<li class='list" + u.id + "'>"
						+ "<label><input type=checkbox class=unit" + u.id 
						+ " name=TopicId value=" + t.id + " " 
						+ (a.topicIds.contains(t.id)?"checked":"")
						+ " onclick=topicClicked('" + u.id + "')"
						+ " /> " + t.title + "</label>"
						+ "</li>");
			}
			buf.append("</ul>"); // topic loop
		}
		
		buf.append("</ul>"  // unit loop
				+ "<input type=submit class='btn btn-primary' value='Click here to assign the topics selected above.' /> "
				+ "<a class='btn btn-primary' href=/exercises?UserRequest=InstructorPage&sig=" + user.getTokenSignature() + ">Quit</a><br/>"
				+ "</form>");
		
		buf.append("\n<script>"  // this script sets up the initial condition for 2-level selectors
				+ "var unitBoxes = document.querySelectorAll('input.unitCheckbox');"
				+ "var topicBoxes, checkall, checkedCount;"
				+ "for (var i=0;i<unitBoxes.length;i++) {\n"
				+ "  topicBoxes = document.querySelectorAll('input.unit' + unitBoxes[i].id);"
				+ "  checkAll = document.getElementById(unitBoxes[i].id);"
				+ "  checkedCount = document.querySelectorAll('input.unit' + unitBoxes[i].id + ':checked').length;"
				+ "  checkAll.checked = checkedCount>0;"
				+ "  checkAll.indeterminate = checkedCount>0 && checkedCount<topicBoxes.length;"
				+ "  var listItems = document.querySelectorAll('li.list' + unitBoxes[i].id);"
				+ "  for (var j=0;j<listItems.length;j++) listItems[j].style='display:' + (checkedCount>0?'list-item':'none');"
				+ "}"
				+ "function topicClicked(unitId) {"
				+ "  var topicCount = document.querySelectorAll('input.unit' + unitId).length;"
				+ "  var checkedCount = document.querySelectorAll('input.unit' + unitId + ':checked').length;"
				+ "  var checkAll = document.getElementById(unitId);"
				+ "  console.log('checked: ' + checkedCount + ' of ' + topicCount);"
				+ "  checkAll.checked = checkedCount>0;"
				+ "  checkAll.indeterminate = checkedCount>0 && checkedCount<topicCount;"
				+ "  if (checkedCount==0) {"
				+ "    var listItems = document.querySelectorAll('li.list' + unitId);"
				+ "    for (var j=0;j<listItems.length;j++) listItems[j].style='display:none';"
				+ "  }"
				+ "}"
				+ "function unitClicked(unitId) {"
				+ "  var unitBox = document.getElementById(unitId);"
				+ "  var listItems = document.querySelectorAll('li.list' + unitId);"
				+ "  var topicBoxes = document.querySelectorAll('input.unit' + unitId);"
				+ "  for (var i=0;i<topicBoxes.length;i++) topicBoxes[i].checked = unitBox.checked;"
				+ "  for (var j=0;j<listItems.length;j++) listItems[j].style='display:' + (unitBox.checked?'list-item':'none');"
				+ "}"
				+ "</script>");
		return buf.toString() + Util.foot();
	}
		
	String reviewScores(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Util.head("Review Scores"));
		buf.append("<h1>Student Scores for This Assignment</h1>");
		
		User user = User.getUser(request.getParameter("sig"));
		if (!user.isInstructor()) return null;
		
		return buf.toString() + Util.foot();
	}
	
}


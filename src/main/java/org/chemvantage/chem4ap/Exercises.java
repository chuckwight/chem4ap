package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
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

@WebServlet(urlPatterns={"/question","/answer"})
public class Exercises extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private Map<Long,Assignment> assignmentMap = new HashMap<Long,Assignment>();
	private Map<String,Score> scoresMap = new HashMap<String,Score>();
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		response.setContentType("application/json");

		JsonObject responseJson = new JsonObject();

		try {
			String token = request.getHeader("Authorization").substring(7);
			String sig = Util.isValid(token);
			responseJson.addProperty("token", Util.getToken(sig));
			
			User user = User.getUser(sig);
			JsonObject q = getCurrentQuestion(user);
			if (q == null) throw new Exception("Unable to get a new question.");
			responseJson.add("question",q);
			out.println(responseJson.toString());
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
			String token = request.getHeader("Authorization").substring(7);
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
			
			Score s = getScore(User.getUser(sig));
			
			StringBuffer buf = new StringBuffer();
			if (q.isCorrect(studentAnswer)) {
				s.update(q,1);
				buf.append("<h2>That's right! Your answer is correct.</h2>");
			} else {
				s.update(q,0);
				buf.append("<h2>Sorry, your answer is not correct</h2>The correct answer is: " + q.getCorrectAnswer());	
			}
			
			JsonObject responseJson = new JsonObject();
			responseJson.addProperty("token",Util.getToken(sig));
			responseJson.addProperty("html", buf.toString());
			out.println(responseJson.toString());
		} catch (Exception e) {
			JsonObject err = new JsonObject();
			err.addProperty("error", e.getMessage());
			out.println(err);
		}
	}
	
	Score getScore(User user) {
		Long assignmentId = user.getAssignmentId();
		Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
		return getScore(user,a);
	}
	
	Score getScore(User user, Assignment a) {
		Key<User> k = key(user);
		Score s = null;
		try {
			s = scoresMap.get(user.hashedId);
			if (s == null) {
				s = ofy().load().type(Score.class).parent(k).id(user.getAssignmentId()).safe();
				scoresMap.put(user.hashedId, s);
			}
		} catch (Exception e) {
			s = new Score(user.hashedId,a);
			s.currentQuestionId = getNewQuestionId(user,a,s);
			s.nextQuestionId = getNewQuestionId(user,a,s);
			scoresMap.put(user.hashedId, s);
			ofy().save().entity(s).now();
		}
		return s;
	}

	JsonObject getCurrentQuestion(User user) throws Exception {
		Assignment a = getAssignment(user.getAssignmentId());
		Score s = getScore(user,a);
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
			JsonArray choices = new JsonArray();
			for (String c : q.choices) choices.add(c);
			j.add("choices", choices);
		}
		return j;
	}
	
	Long getNewQuestionId(User user, Assignment a, Score s) {		
		// Select a topic based on current topic scores
		Long topicId = null;
		if (!s.topicIds.equals(a.topicIds)) s.repairMe(a);
		int range = 0;
		Random random = new Random();
		for (Long tId : a.topicIds) {
			range += 100 - s.scores.get(tId);
		}
		int r = random.nextInt(range);
		range = 0;
		for (Long tId : a.topicIds) {
			range += 100 - s.scores.get(tId);
			if (r < range) {
				topicId = tId;
				break;
			}
		}
		
		// Gather question keys for types based on quartile score for the topic
		// Quartile 1 gets TF & MC, Q2 gets MC & FB, Q3 gets FB & CB, Q4 gets CB & NU
		int quartile = s.scores.get(topicId)/4 + 1;
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		switch (quartile) {
		case 1:
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId",topicId).filter("type","true_false").keys().list());
		case 2:
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId",topicId).filter("type","multiple_choice").keys().list());
			if (quartile == 1) break;
		case 3:
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId",topicId).filter("type","fill_in_blank").keys().list());
			if (quartile == 2) break;
		case 4:
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId",topicId).filter("type","checkbox").keys().list());
			if (quartile == 3) break;
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId",topicId).filter("type","numeric").keys().list());
		}
		
		// Eliminate any questions recently answered
		questionKeys.removeAll(s.answeredQuestionKeys.get(topicId));
		// Serlerct one key at random and convert it to a Long id
		Long questionId = questionKeys.get(random.nextInt(questionKeys.size())).getId();
		
		return questionId;
	}
	
	Assignment getAssignment(Long id) {
		Assignment a = assignmentMap.get(id);
		if (a == null) {
			a = ofy().load().type(Assignment.class).id(id).now();
			assignmentMap.put(id, a);
		}
		return a;
	}
}


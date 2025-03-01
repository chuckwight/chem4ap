package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
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
		StringBuffer debug = new StringBuffer();
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
			debug.append("1");
			
			Score s = getScore(User.getUser(sig));
			boolean correct = q.isCorrect(studentAnswer);
			s.update(q, q.isCorrect(studentAnswer)?1:0);
			
			StringBuffer buf = new StringBuffer();
			buf.append(correct?"<h2>That's right! Your answer is correct.</h2>":
				"<h2>Sorry, your answer is not correct</h2>The correct answer is " + q.getCorrectAnswer() + "<br/>");
			buf.append("Your score on this assignment is " + s.totalScore + "%");
			
			JsonObject responseJson = new JsonObject();
			responseJson.addProperty("token",Util.getToken(sig));
			responseJson.addProperty("html", buf.toString());
			out.println(responseJson.toString());
		} catch (Exception e) {
			JsonObject err = new JsonObject();
			err.addProperty("error", e.getMessage()); // + debug.toString());
			out.println(err);
		}
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

	Score getScore(User user) throws Exception {
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		return getScore(user,a);
	}
	
	Score getScore(User user, Assignment a) throws Exception {
		Score s = null;
		Key<Score> scoreKey = key(key(user), Score.class, a.id);
		try {
			s = ofy().load().key(scoreKey).safe();
			if (!s.topicIds.equals(a.topicIds)) s.repairMe(a); 
		} catch (Exception e) {
			s = new Score(user.hashedId,a);
			ofy().save().entity(s).now();
		}
		return s;
	}
}


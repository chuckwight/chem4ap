package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
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
	
	private Map<Long,Question> questionMap = new HashMap<Long,Question>();
	private Map<Long,Assignment> assignmentMap = new HashMap<Long,Assignment>();
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		response.setContentType("application/json");

		JsonObject responseJson = new JsonObject();

		try {
			String token = request.getHeader("Authorization");
			if (token != null) token = token.substring(7);
			String sig = Util.isValid(token);

			responseJson.addProperty("token", Util.getToken(sig));

			User user = User.getUser(sig);
			responseJson.add("question",getNextQuestion(user));

			out.println(responseJson.toString());
		} catch (Exception e) {
			response.sendError(401);	//responseJson.addProperty("error", "You must launch this app from the link inside your LMS.");
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		
		try {
			String token = request.getHeader("Authorization");
			if (token != null) token = token.substring(7);
			String sig = Util.isValid(token);

			BufferedReader reader = request.getReader();
			JsonObject requestJson = JsonParser.parseReader(reader).getAsJsonObject();
			
			Long questionId = requestJson.get("id").getAsLong();
			Long parameter = requestJson.get("parameter").getAsLong();
			String studentAnswer = requestJson.get("answer").getAsString();
			
			Question q = getQuestion(questionId);
			if (q.requiresParser()) q.setParameters(parameter);
			
			StringBuffer buf = new StringBuffer();
			if (q.isCorrect(studentAnswer)) {
				buf.append("<h2>That's right! Your answer is correct.</h2>");
			} else {
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
	
	Question getQuestion(Long id) {
		Question q = questionMap.get(id);
		if (q == null) {
			q = ofy().load().type(Question.class).id(id).now();
			questionMap.put(id, q);
		}
		return q;
	}
	
	JsonObject getNextQuestion(User user) {
		Question q = null;
		Assignment a = getAssignment(user.getAssignmentId());
		Key<Question> k = a.questionKeys.get(new Random().nextInt(a.questionKeys.size()));
		q = questionMap.get(k.getId());
		if (q == null) {
			q = ofy().load().key(k).now();
			questionMap.put(k.getId(), q);
		}
		Gson gson = new Gson();
		JsonObject j = JsonParser.parseString(gson.toJson(q)).getAsJsonObject();
		return j;
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


package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
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
	
	private Map<Long,Question> questionMap = new HashMap<Long,Question>();
	private Map<Long,Assignment> assignmentMap = new HashMap<Long,Assignment>();
	
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
			JsonObject q = getNextQuestion(user);
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
			
			Question q = getQuestion(questionId);
			if (q.requiresParser()) {
				Integer parameter = requestJson.get("parameter").getAsInt();
				q.setParameters(parameter);
			}
			
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
	
	JsonObject getNextQuestion(User user) throws Exception {
		Question q = null;
		Assignment a = getAssignment(user.getAssignmentId());
		Key<Question> k = a.questionKeys.get(new Random().nextInt(a.questionKeys.size()));
		if (k == null) return null;
		
		q = questionMap.get(k.getId());
		if (q == null) {
			q = ofy().load().key(k).now();
			questionMap.put(k.getId(), q);
		}
		if (q==null) return null;
		
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
	
	Assignment getAssignment(Long id) {
		Assignment a = assignmentMap.get(id);
		if (a == null) {
			a = ofy().load().type(Assignment.class).id(id).now();
			assignmentMap.put(id, a);
		}
		return a;
	}
}


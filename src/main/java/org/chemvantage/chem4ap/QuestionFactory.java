package org.chemvantage.chem4ap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@WebServlet(urlPatterns={"/question","/answer"})
public class QuestionFactory extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	static List<JsonObject> questions = new ArrayList<JsonObject>();
	static {
		if (questions.isEmpty()) {
			JsonObject question = null;
			question = new JsonObject();
			question.addProperty("prompt", "What is the atomic number of Boron?");
			question.addProperty("correctAnswer", 5);
			question.addProperty("id",  '0');
			questions.add(question);
			question = new JsonObject();
			question.addProperty("prompt", "A ________ is an ordinary phase of matter that does not adopt the shape of its container.");
			question.addProperty("correctAnswer", "solid");
			question.addProperty("id",  '1');
			questions.add(question);
			question = new JsonObject();
			question.addProperty("prompt", "A student claims that the SI unit of mass is 1 g. True or false?");
			question.addProperty("correctAnswer", false);
			question.addProperty("id",  '2');
			questions.add(question);			
		}
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		
		JsonObject responseJson = new JsonObject();
		
		try {
			String token = request.getHeader("Authorization");
			if (token != null) token = token.substring(7);
			String sig = isValid(token);
			
			responseJson.addProperty("token", Util.getToken(sig));
			
			int rand = new Random().nextInt(questions.size());
			responseJson.add("question", questions.get(rand));

			out.println(responseJson.toString());
		} catch (Exception e) {
			response.sendError(401);	//responseJson.addProperty("error", "You must launch this app from the link inside your LMS.");
			//out.println(responseJson.toString());
			
			// first call arrives here
			//response.setContentType("text/html");
			//out.println(e.toString() + "<br/>" + e.getMessage());
			//response.sendRedirect("/?t=" + getToken("chuck"));
			//JsonObject err = new JsonObject();
			//err.addProperty("error", e.getMessage());
			//out.println(err);
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		
		try {
			String token = request.getHeader("Authorization").substring(7);
			if(token == null) throw new Exception();
			
			BufferedReader reader = request.getReader();
			JsonObject requestJson = JsonParser.parseReader(reader).getAsJsonObject();
			
			//String token = requestJson.get("token").getAsString();
			int questionId = requestJson.get("id").getAsInt();
			String studentAnswer = requestJson.get("answer").getAsString();
			
			JsonObject question = questions.get(questionId);
			StringBuffer buf = new StringBuffer();
			if (question.get("correctAnswer").getAsString().equals(studentAnswer)) {
				buf.append("<h2>Congratulations</h2>Your answer is correct.");
			} else {
				buf.append("<h2>Sorry, your answer is not correct</h2");
			}
			
			JsonObject responseJson = new JsonObject();
			responseJson.addProperty("token",Util.getToken("chuck"));
			responseJson.addProperty("html", buf.toString());
			out.println(responseJson.toString());
		
		} catch (Exception e) {
			JsonObject err = new JsonObject();
			err.addProperty("error", e.getMessage());
			out.println(err);
		}
	}
	
	protected String isValid(String token) throws Exception {
		Algorithm algorithm = Algorithm.HMAC256(Util.getHMAC256Secret());
		JWTVerifier verifier = JWT.require(algorithm).build();
		verifier.verify(token);
		DecodedJWT payload = JWT.decode(token);
		String nonce = payload.getId();
		if (!Nonce.isUnique(nonce)) throw new Exception("Token was used previously.");
		// return the user's tokenSignature
		return payload.getSubject();
	}

	JsonObject scoreAnswer(String sig, Long questionId, Long parameter, String studentAnswer) {
		JsonObject score = new JsonObject();
		JsonObject question = questions.get(questionId.intValue());
		question.addProperty("studentAnswer", studentAnswer);
		score.addProperty("score", isCorrect(question)?1:0);
		return score;
	}
	
	boolean isCorrect(JsonObject question) {
		String correctAnswer = question.get("correctAnswer").getAsString();
		String studentAnswer = question.get("studentAnswer").getAsString();
		
		return correctAnswer.equals(studentAnswer);
	}
}

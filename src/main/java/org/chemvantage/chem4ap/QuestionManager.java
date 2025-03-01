package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/questions")
public class QuestionManager extends HttpServlet {

	private static final long serialVersionUID = 1L;
	List<APChemUnit> unitList = null;
	List<APChemTopic> topicList = null;
	Map<Long,APChemTopic> topicMap = null;

	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = "";
		
		try {
			switch (userRequest) {
			case "EditQuestion":
				Long questionId = null;
				questionId = Long.parseLong(request.getParameter("QuestionId")); 
				out.println(editQuestion(questionId));
				break;
			case "NewQuestion": 
				out.println(newQuestionForm(request)); 
				break;
			case "NewJson":
				out.println(newJsonForm(request));
				break;
			case "Preview":
				out.println(previewQuestion(request));
				break;
			default:
				String assignmentType = request.getParameter("AssignmentType");
				Long topicId = null;
				Long unitId = null;
				try {
					unitId = Long.parseLong(request.getParameter("UnitId"));
				} catch (Exception e) {}
				try {
					topicId = Long.parseLong(request.getParameter("TopicId")); 
					if (unitId == null) {
						APChemTopic topic = ofy().load().type(APChemTopic.class).id(topicId).safe();
						unitId = topic.unitId;
					}
				} catch (Exception e) {}
				out.println(viewQuestions(assignmentType,unitId,topicId));
			}
		} catch (Exception e) {
			response.getWriter().println("Chem4AP Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
	}
		
	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) return;
		
		try {
			switch (userRequest) {
			case "Preview":
				break;
			case "Save New Question":
				createQuestion(request);
				break;
			case "Update Question":
				updateQuestion(request);
				break;
			case "Delete Question":
				deleteQuestion(request);
				break;
			}
		} catch (Exception e) {
			response.getWriter().println(e.getMessage()==null?e.toString():e.getMessage());
		}
		doGet(request,response);
	}
	
	Question assembleQuestion(HttpServletRequest request) {
		try {
			String type = request.getParameter("QuestionType");
			return assembleQuestion(request,new Question(type)); 
		} catch (Exception e) {
			return null;
		}
	}
	
	Question assembleQuestion(HttpServletRequest request,Question q) {
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e) {}
		String type = q.type;
		try {
			type = request.getParameter("QuestionType");
		}catch (Exception e) {}
		String prompt = request.getParameter("Prompt");
		ArrayList<String> choices = new ArrayList<String>();
		char choice = 'A';
		for (int i=0;i<5;i++) {
			String choiceText = request.getParameter("Choice"+ choice +"Text");
			if (choiceText==null) choiceText = "";
			if (choiceText.length() > 0) {
				choices.add(choiceText);
			}
			choice++;
		}
		double requiredPrecision = 0.; // percent
		int significantFigures = 0;
		try {
			requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
		} catch (Exception e) {
		}
		try {
			significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
		} catch (Exception e) {
		}
		String correctAnswer = "";
		try {
			String[] allAnswers = request.getParameterValues("CorrectAnswer");
			for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
		} catch (Exception e) {
			correctAnswer = request.getParameter("CorrectAnswer");
			if (correctAnswer == null) correctAnswer = "";
		}
		String parameterString = request.getParameter("ParameterString");
		if (parameterString == null) parameterString = "";
		
		q.assignmentType = request.getParameter("AssignmentType");
		q.topicId = topicId;
		q.type = type;
		q.prompt = prompt;
		q.choices = choices;
		q.requiredPrecision = requiredPrecision;
		q.significantFigures = significantFigures;
		q.correctAnswer = correctAnswer;
		q.units = request.getParameter("Units");
		q.parameterString = parameterString;
		q.scrambleChoices = Boolean.parseBoolean(request.getParameter("ScrambleChoices"));
		return q;
	}
	
	void createQuestion(HttpServletRequest request) { //previously type long
		try {
			Question q = assembleQuestion(request);
			ofy().save().entity(q).now();
	} catch (Exception e) {}
	}

	void deleteQuestion(HttpServletRequest request) throws Exception {
		Long questionId = Long.parseLong(request.getParameter("QuestionId"));
		ofy().delete().type(Question.class).id(questionId).now();
	}
	
	String editQuestion(Long questionId) throws Exception {
		StringBuffer buf = new StringBuffer(Util.head("Editor"));
		
		Question q = ofy().load().type(Question.class).id(questionId).safe();
		
		if (topicMap == null) refreshTopics();
		APChemTopic t = topicMap.get(q.topicId);
		
		if (q.requiresParser()) q.setParameters();
		buf.append("<h1>Edit</h1><h2>Current Question</h2>");
		buf.append("Topic: " + (t==null?"n/a":t.title) + "<br/>");
		
		buf.append("Success Rate: " + q.getSuccess() + "<p>");
		
		buf.append("<FORM Action=/questions METHOD=POST>");
		
		buf.append(q.printAll());
		
		buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + " />"
				+ "<input type=hidden name=AssignmentType value=" + q.assignmentType + " />");
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question' />");
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit' />");
		
		buf.append("<hr><h2>Edit This Question</h2>");
		
		buf.append("Topic:" + topicSelectBox(q.topicId) + "<br/>");
		
		buf.append("Question Type:" + questionTypeDropDownBox(q.type) + "<br/>");
		
		buf.append(q.edit());
		
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview />");
		buf.append("</FORM>");
	
		return buf.toString() + Util.foot();
	}
	
	String newJsonForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Util.head("Editor"));
		buf.append("<h1>Edit</h1><h2>New Question</h2>");
		
		try {
			Long topicId = Long.parseLong(request.getParameter("TopicId"));
			APChemTopic topic = ofy().load().type(APChemTopic.class).id(topicId).safe();
			JsonObject jq = JsonParser.parseString(request.getParameter("json")).getAsJsonObject();
			String questionType = jq.get("type").getAsString();
			switch (questionType) {
			case "multiple_choice": 
				buf.append("<h3>Multiple-Choice Question</h3>");
				buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to select the single best "
					+ "answer to the question."); break;
			case "true_false": 
				buf.append("<h3>True-False Question</h3>");
				buf.append("Write the question as an affirmative statement. Then "
					+ "indicate below whether the statement is true or false."); break;
			case "checkbox": 
				buf.append("<h3>Select-Multiple Question</h3>");
				buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to "
					+ "select all of the correct answers to the question."); break;
			case "fill_in_blank": 
				buf.append("<h3>Fill-in-Blank Question</h3>");
				buf.append("Write the prompt as a sentence containing a blank (________). "
					+ "Write the correct answer (and optionally, an alternative correct answer) in "
					+ "the box below using commas to separate the correct options.  The answers "
					+ "are not case-sensitive or punctuation-sensitive."); break;
			case "numeric": 
				buf.append("<h3>Numeric Question</h3>");
				buf.append("Fill in the question text in the upper textarea box and "
					+ "the correct numeric answer below. Also indicate the required precision "
					+ "of the student's response in percent (default = 2%). Use the bottom "
					+ "textarea box to finish the question text and/or to indicate the "
					+ "expected dimensions or units of the student's answer."); break;
			case "essay": 
				buf.append("<h3>EssayQuestion</h3>");
				buf.append("Fill in the question text. The user will be asked to provide a short "
					+ "essay response."); break;
			default: buf.append("An unexpected error occurred. Please try again.");
			}
			Question question = new Question(questionType);
			buf.append("<p><FORM METHOD=POST ACTION=/questions>");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + " />"
					+ "<input type=hidden name=AssignmentType value=" + request.getParameter("AssignmentType") + " />");
			buf.append("Topic: " + topic.title + "<br>"
					+ "<input type=hidden name=TopicId value=" + topic.id + " />");
			
			buf.append(question.edit(jq));
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview'></FORM>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	String newQuestionForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Util.head("Editor"));
		
		buf.append("<h1>Edit</h1><h2>New Question</h2>");
		Long topicId = null;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e) {}		
		APChemTopic topic = ofy().load().type(APChemTopic.class).id(topicId).safe();
		
		String questionType = null;;		
		try {
			questionType = request.getParameter("QuestionType");
			switch (questionType) {
			case "multiple_choice": 
				buf.append("<h3>Multiple-Choice Question</h3>");
				buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to select the single best "
					+ "answer to the question."); break;
			case "true_false": 
				buf.append("<h3>True-False Question</h3>");
				buf.append("Write the question as an affirmative statement. Then "
					+ "indicate below whether the statement is true or false."); break;
			case "checkbox": 
				buf.append("<h3>Select-Multiple Question</h3>");
				buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to "
					+ "select all of the correct answers to the question."); break;
			case "fill_in_blank": 
				buf.append("<h3>Fill-in-Blank Question</h3>");
				buf.append("Write the prompt as a sentence containing a blank (________). "
					+ "Write the correct answer (and optionally, an alternative correct answer) in "
					+ "the box below using commas to separate the correct options.  The answers "
					+ "are not case-sensitive or punctuation-sensitive."); break;
			case "numeric": 
				buf.append("<h3>Numeric Question</h3>");
				buf.append("Fill in the question text in the upper textarea box and "
					+ "the correct numeric answer below. Also indicate the required precision "
					+ "of the student's response in percent (default = 2%). Use the bottom "
					+ "textarea box to finish the question text and/or to indicate the "
					+ "expected dimensions or units of the student's answer."); break;
			case "essay": 
				buf.append("<h3>EssayQuestion</h3>");
				buf.append("Fill in the question text. The user will be asked to provide a short "
					+ "essay response."); break;
			default: buf.append("An unexpected error occurred. Please try again.");
			}
			Question question = new Question(questionType);
			buf.append("<p><FORM METHOD=POST ACTION=/questions>");
			buf.append("<input type=hidden name=AssignmentType value=" + request.getParameter("AssignmentType") + " />"
					+ "<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + " />");
			buf.append("Topic: " + topic.title + "<br>"
					+ "<input type=hidden name=TopicId value=" + topic.id + " />");
			
			buf.append(question.edit());
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview'></FORM>");
		} catch (Exception e) {
			buf.append("Topic: " + topic.title);
			buf.append("<form method=get>"
					+ "<input type=hidden name=UserRequest value='NewQuestion' />"
					+ "<input type=hidden name=AssignmentType value=" + request.getParameter("AssignmentType") + " />"
					+ "<input type=hidden name=TopicId value=" + topicId + " />"
					+ "Select a question type: " + questionTypeDropDownBox("")
					+ "<input type=submit value=Go /></form>");
			}
		return buf.toString() + Util.foot();
	}

	String previewQuestion(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Util.head("Editor"));
		try {
			String assignmentType = request.getParameter("AssignmentType");
			long questionId = 0;
			boolean current = false;
			boolean proposed = false;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
				current = true;
			} catch (Exception e2) {}
			long proposedQuestionId = 0;
			try {
				proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				proposed = true;
				current = false;
			} catch (Exception e2) {}
			
			Long topicId = null;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e) {}
			
			Question q = assembleQuestion(request);
			if (q.requiresParser()) q.setParameters();
			
			buf.append("<h1>Edit</h1><h2>Preview Question</h2>");
			
			APChemTopic t = (topicId==null?null:ofy().load().type(APChemTopic.class).id(topicId).now());
			buf.append("Topic: " + (t==null?"n/a":t.title) + "<br/>");
			
			buf.append("<FORM ACTION=/questions METHOD=POST>");
			
			buf.append(q.printAll());
			
			if (current) {
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + ">");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Question'>");
			}
			if (proposed) {
				buf.append("<INPUT TYPE=HIDDEN NAME=ProposedQuestionId VALUE=" + proposedQuestionId + ">");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Activate This Question'>");
			} else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save New Question'>");
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit'>");
			
			buf.append("<hr><h2>Continue Editing</h2>");
			buf.append("AssignmentType: " + assignmentType + "<br/>"
					+ "<input type=hidden name=AssignmentType value=" + assignmentType + " />");
			buf.append("Topic:" + topicSelectBox(topicId) + "<br/>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.type) + "<br/>");
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString() + Util.foot();
	}

	String questionTypeDropDownBox(String questionType) {
		StringBuffer buf = new StringBuffer();
		buf.append("\n<SELECT NAME=QuestionType><option>Select a question type</option>"
				+ "<OPTION VALUE=multiple_choice" + ("multiple_choice".equals(questionType)?" SELECTED>":">") + "Multiple Choice</OPTION>"
				+ "<OPTION VALUE=true_false" + ("true_false".equals(questionType)?" SELECTED>":">") + "True/False</OPTION>"
				+ "<OPTION VALUE=checkbox" + ("checkbox".equals(questionType)?" SELECTED>":">") + "Checkbox</OPTION>"
				+ "<OPTION VALUE=fill_in_blank" + ("fill_in_blank".equals(questionType)?" SELECTED>":">") + "Fill in Blank</OPTION>"
				+ "<OPTION VALUE=numeric" + ("numeric".equals(questionType)?" SELECTED>":">") + "Numeric</OPTION>"
				+ "<OPTION VALUE=essay" + ("essay".equals(questionType)?" SELECTED>":">") + "Essay</OPTION>"
				+ "</SELECT>");
		return buf.toString();
	}
	
	void refreshTopics() {
		unitList = ofy().load().type(APChemUnit.class).order("unitNumber").list();
		topicList = ofy().load().type(APChemTopic.class).list();
		topicMap = new HashMap<Long,APChemTopic>();
		for (APChemTopic t : topicList) topicMap.put(t.id, t);
	}

	String topicSelectBox(Long topicId) {
		StringBuffer buf = new StringBuffer("<select name=TopicId>");
		if (topicList == null) refreshTopics();
		for (APChemTopic t : topicList) buf.append("<option value=" + t.id + (t.id.equals(topicId)?" selected>":">") + t.title + "</option>");
		buf.append("</select>");
		return buf.toString();
	}

	void updateQuestion(HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			Question q = ofy().load().type(Question.class).id(questionId).safe();
			q = assembleQuestion(request,q);
			ofy().save().entity(q).now();
		} catch (Exception e) {
			return;
		}
	}

	String viewQuestions(String assignmentType,Long unitId, Long topicId) throws Exception {
		StringBuffer buf = new StringBuffer(Util.head("Editor"));

		buf.append("<h1>Manage Question Items</h1>");

		//if (topicMap == null) refreshTopics();
		refreshTopics();	
		buf.append("<h2>Question Items</h2>");
		APChemUnit unit = null;
		APChemTopic topic = null;
		
		buf.append("<form><input type=hidden name=UserRequest value=ViewQuestions />");
		
		if (assignmentType == null) {
			buf.append("<label><input type=radio required name=AssignmentType value=Exercises onClick=submit() />Exercises</label><br/>"
					+ " <label><input type=radio required name=AssignmentType value=Homework onClick=submit() />Homework</label>");
		} else {
			buf.append("<h3>Exercises</h3>"
					+ "<input type=hidden name=AssignmentType value=" + assignmentType + " />");

			// Make a drop-down selector for units
			unit = unitId==null?null:ofy().load().type(APChemUnit.class).id(unitId).now();
			buf.append("<select name=UnitId onchange=submit()><option>Select a Unit</option>");
			for (APChemUnit u : unitList) buf.append("<option value=" + u.id + (u.equals(unit)? " selected":"") + ">" + u.title + "</option>");
			buf.append("</select>&nbsp;");

			if (unitId !=null) {
				topicList = ofy().load().type(APChemTopic.class).filter("unitId",unit.id).order("topicNumber").list();
				// Make a drop-down selector for units
				topic = topicId==null?null:ofy().load().type(APChemTopic.class).id(topicId).now();
				buf.append("<select name=TopicId onchange=submit()><option>Select a topic</option>");
				for (APChemTopic t : topicList) buf.append("<option value=" + t.id + (t.equals(topic)? " selected":"") + ">" + t.title + "</option>");
				buf.append("</select>");
			}
		}
		buf.append("</form>");

		if (unit != null && topic != null) {  // display the questions
			List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).list();
			buf.append("This topic has " + questions.size() + " question items.<br/>");
			
			buf.append("<a href=/questions?UserRequest=NewQuestion&AssignmentType=" + assignmentType + "&TopicId=" + topic.id + ">Create a New Question</a> ");	
			buf.append("<form method=get style='display: inline;'>"
					+ "<input type=hidden name=UserRequest value='NewJson' />"
					+ "<input type=hidden name=AssignmentType value=" + assignmentType + " />"
					+ "<input type=hidden name=TopicId value=" + topicId + " />"
					+ "or paste a JSON string here: <input type=text name=json />"
					+ "<input type=submit value=Go /></form>");
			
			buf.append("<table>");
			
			// Count the number of question of each type:
			int tf = 0;
			int mc = 0;
			int fb = 0;
			int cb = 0;
			int nu = 0;
			
			for (Question q : questions) {
				switch (q.type) {
				case "true_false": tf++; break;
				case "multiple_choice": mc++; break;
				case "fill_in_blank": fb++; break;
				case "checkbox": cb++; break;
				case "numeric": nu++; break;
				}
				
				q.setParameters();
				buf.append("<tr>"
					+ "<td style='vertical-align: top;'>"
					+ "  <form method=get action=/questions>"
					+ "    <input type=hidden name=QuestionId value=" + q.id + " />"
					+ "    <input type=hidden name=UserRequest value=EditQuestion /><br/>"
					+ "    <input type=submit value=Edit />"
					+ "  </form>"
					+ "</td>"
					+ "<td>" + q.printAll() + "</td>"
					+ "</tr>");
			}
			buf.append("</table>");
			
			buf.append("Number of each type: TF-" + tf + " MC-" + mc + " FB-" + fb + " CB-" + cb + " NU-" + nu);
		}
		return buf.toString() + Util.foot();
	}
	
	class SortByPctSuccess implements Comparator<Question> {
		public int compare(Question q1,Question q2) {
			int rank = q2.getPctSuccess() - q1.getPctSuccess(); // sort more successful questions first
			if (rank==0) rank = q1.id.compareTo(q2.id); // tie breaker			
			return rank;  
		}
	}
}

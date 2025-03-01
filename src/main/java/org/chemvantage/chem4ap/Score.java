package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;

@Entity
public class Score {    
	@Id	Long id;  // assignmentId
	@Parent Key<User> owner;
	int totalScore = 0;
	int maxScore = 0; // totalScore can decrease, so only report maxScore to the LMS
	List<Long> topicIds = new ArrayList<Long>();
	List<Integer> topicScores = new ArrayList<Integer>();  // each 0-100 in order of topicId
	List<Key<Question>> recentQuestionKeys = new ArrayList<Key<Question>>();
	Long currentQuestionId;
	
	Score() {}
	
	Score(String hashedId, Assignment a) throws Exception {
		id = a.id;
		owner = key(User.class,hashedId);
		topicIds = a.topicIds;
		for (int i = 0; i < topicIds.size(); i++) topicScores.add(0);
		currentQuestionId = getNewQuestionId();
	}
	
	void repairMe(Assignment a) {
		/* 
		 * This method is required in the event that the instructor changes the topics
		 * covered after a user has started the assignment.
		 * Required changes:
		 * 1) replace topicIds with the one from the assignment
		 * 2) shuffle the scores to match the new list, discard obsolete, add new [0]
		 */
		List<Integer> newTopicScores = new ArrayList<Integer>();
		for (Long tId : a.topicIds) {
			if (topicIds.contains(tId)) newTopicScores.add(topicScores.get(topicIds.indexOf(tId)));
			else newTopicScores.add(0);
		}
		topicIds = a.topicIds;
		topicScores = newTopicScores;
		ofy().save().entity(this).now();
	}

	void update(Question q, int qScore) throws Exception {
		/* Algorithm for tracking topic scores and totalScore:
		 * The totalScore is calculated as a running average
		 *   totalScore = (120*qScore + 11*totalScore)/12;
		 * When qScore=1, starts with 10% increase, last is 1% increase to 100.
		 * The value of totalScore is not permitted to exceed 100
		 * or to go below the floor of the current quartile score.
		 * 
		 * Each topic score is also tracked separately using
		 *   topicScore = (100*qScore + 2*topicScore)/3;
		 * This increases three times faster, but never gets to 100
		 * The topicScores are used to select a topic for the next question:
		 * 
		 * The currentQuestionId is saved so a question can't be skipped
		 * simply by reloading the page or app.
		 */
		
		// Update the totalScore:
		int floor = totalScore/25*25;  // 0, 25, 50, 75, 100
		totalScore = (120*qScore + 11*totalScore)/12;
		if (totalScore > 100) totalScore = 100;
		if (totalScore < floor) totalScore = floor;
		
		// Update the topicScore
		Long topicId = q.topicId;
		int index = topicIds.indexOf(topicId);
		topicScores.set(index, (100*qScore + 2*topicScores.get(index))/3);
		
		// Select a new questionId
		currentQuestionId = getNewQuestionId();
		ofy().save().entity(this);
		
		// Create a Task to report the score to the LMS
		if (totalScore > maxScore) { // only report if maxScore increases
			maxScore = totalScore;
			User user = ofy().load().key(owner).now();
			String payload = "AssignmentId=" + id + "&UserId=" + URLEncoder.encode(user.getId(),"UTF-8");
			Util.createTask("/report",payload);
		}
	}
	
	Long getNewQuestionId() throws Exception {
		/* Algorithm for selecting the next question:
		 * 1) Select a topic:
		 *    Each topic gets a weight of 100-topicScore and the topic
		 *    is selected at random from the percentages of the total weight.
		 *    This favors topics which have low scores, either because
		 *    few questions were presented (distribution) or because
		 *    the student didn't answer correctly (adaptation).
		 * 2) Select a question type:
		 *    This is based on the totalScore by quartile:
		 *    Q1: true_false and multiple_choice
		 *    Q2: multiple_choice and fill_in_blank
		 *    Q3: fill_in_blank and checkbox
		 *    Q4: checkbox and numeric
		 */
		// Select a topic based on current topic scores
		Long topicId = null;
		int range = 0;
		for (Long tId : topicIds) {
			range += 100 - topicScores.get(topicIds.indexOf(tId));
		}
		Random random = new Random();
		if (range > 0) {
			int r = random.nextInt(range);
			range = 0;
			for (Long tId : topicIds) {
				range += 100 - topicScores.get(topicIds.indexOf(tId));
				if (r < range) {
					topicId = tId;
					break;
				}
			}
		} else topicId = topicIds.get(random.nextInt(topicIds.size()));

		// Gather question keys for types based on quartile score for the topic
		// Quartile 1 gets TF & MC, Q2 gets MC & FB, Q3 gets FB & CB, Q4 gets CB & NU
		int quartile = totalScore/25 + 1;
		if (quartile > 4) quartile = 4;

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

		// Eliminate any questions recently answered or on deck
		questionKeys.removeAll(recentQuestionKeys);

		// Select one key at random and convert it to a Long id
		Key<Question> k = questionKeys.get(random.nextInt(questionKeys.size()));

		// Add the key to the list of recent and trim, if necessary
		recentQuestionKeys.add(k);
		if (recentQuestionKeys.size() > 5) recentQuestionKeys.remove(0);

		return k.getId();
	}
}
package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.annotation.Serialize;

@Entity
public class Score {    
	@Id	Long id;  // assignmentId
	@Parent Key<User> owner;
	
	List<Long> topicIds = new ArrayList<Long>();
	List<Key<Question>> answeredQuestionKeys = new ArrayList<Key<Question>>();
	@Serialize
	Map<Long,Integer> scores = new HashMap<Long,Integer>();  // scores 0-100 by topicId
	Long currentQuestionId;
	Long nextQuestionId;
	
	Score() {}
	
	Score(String hashedId, Assignment a) throws Exception {
		id = a.id;
		owner = key(User.class,hashedId);
		topicIds = a.topicIds;
		for (Long topicId : topicIds) {
			scores.put(topicId, 0);
		}
		currentQuestionId = getNewQuestionId();
		nextQuestionId = getNewQuestionId();
	}
	
	int getPctScore() {
		int score = 0;
		for (Long topicId : topicIds) score += scores.get(topicId);
		return score/topicIds.size();
	}
	
	void repairMe(Assignment a) {
		for (Long tId : this.topicIds) {
			if (!a.topicIds.contains(tId)) {  // remove any data from deleted topics
				this.scores.remove(tId);
			}
		}
		for (Long tId : a.topicIds) {  // add new data for topics that were added
			if (!this.topicIds.contains(tId)) {
				this.scores.put(tId,0);
			}
		}
		this.topicIds = a.topicIds;
		ofy().save().entity(this).now();
	}

	void update(Question q, int qScore) throws Exception {
		StringBuffer debug = new StringBuffer("Score.update");
		try {
		Long topicId = q.topicId;
		int quartile = this.scores.get(topicId)/25 + 1;
		int n = (21-3*quartile)/2; // averaging constant - range 9-4 
		int proposedScore = (120*qScore + n*scores.get(topicId))/(n+1);
		if (proposedScore > 100) proposedScore = 100;
		int newQuartile = proposedScore/25 + 1;
		if (newQuartile < quartile) proposedScore = (quartile-1)*25;
		scores.put(topicId,proposedScore);
		answeredQuestionKeys.add(key(q));
		currentQuestionId = nextQuestionId;
		debug.append("i");
		nextQuestionId = getNewQuestionId();
		ofy().save().entity(this);
		} catch (Exception e) {
			throw new Exception(e.getMessage() + debug.toString());
		}
	}
	
	Long getNewQuestionId() throws Exception {
		StringBuffer debug = new StringBuffer("NewQuestionId:");
		try {
		// Select a topic based on current topic scores
		Long topicId = null;
		int range = 0;
		Random random = new Random();
		for (Long tId : topicIds) {
			range += 100 - scores.get(tId);
		}
		debug.append("range="+range);
		int r = random.nextInt(range);
		range = 0;
		for (Long tId : topicIds) {
			range += 100 - scores.get(tId);
			if (r < range) {
				topicId = tId;
				break;
			}
		}
		debug.append(" topicId=" + topicId);
		// Gather question keys for types based on quartile score for the topic
		// Quartile 1 gets TF & MC, Q2 gets MC & FB, Q3 gets FB & CB, Q4 gets CB & NU
		int score = scores.get(topicId);
		int quartile = scores.get(topicId)/4 + 1;
		debug.append(" score=" + score + " quartile=" + quartile);
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		try {
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
		debug.append(" keys=" + questionKeys.size());
		
		} catch (Exception e) {
			debug.append("out");
			questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exercises").filter("topicId", topicId).keys().list());
		}
		// Eliminate any questions recently answered
		questionKeys.removeAll(answeredQuestionKeys);
		debug.append(" " + questionKeys.size() + " keys");
		// Select one key at random and convert it to a Long id
		Long questionId = questionKeys.get(random.nextInt(questionKeys.size())).getId();
		return questionId;
		} catch (Exception e) {
			throw new Exception(e.getMessage() + debug.toString());
		}
	}
	

}
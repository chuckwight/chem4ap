package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

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
	
	List<Long> topicIds = new ArrayList<Long>();
	List<Integer> scores = new ArrayList<Integer>();  // each 0-100 in order of topicId
	List<Key<Question>> recentQuestionKeys = new ArrayList<Key<Question>>();
	Long currentQuestionId;
	Long nextQuestionId;
	
	Score() {}
	
	Score(String hashedId, Assignment a) throws Exception {
		id = a.id;
		owner = key(User.class,hashedId);
		topicIds = a.topicIds;
		for (int i = 0; i < topicIds.size(); i++) scores.add(0);
		currentQuestionId = getNewQuestionId();
		nextQuestionId = getNewQuestionId();
	}
	
	int getPctScore() {
		int score = 0;
		for (int i = 0; i < topicIds.size(); i++) score += scores.get(i);
		return score/topicIds.size();
	}
/*	
	void repairMe(Assignment a) {
		for (Long tId : this.topicIds) {
			if (!a.topicIds.contains(tId)) {  // remove any data from deleted topics
				this.scores.remove(topicIds.indexOf(tId));
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
*/
	void update(Question q, int qScore) throws Exception {
		Long topicId = q.topicId;
		int index = topicIds.indexOf(topicId);
		int quartile = scores.get(index)/25 + 1;
		int n = 9;
		int proposedScore = (120*qScore + n*scores.get(index))/(n+1);
		if (proposedScore > 100) proposedScore = 100;
		int newQuartile = proposedScore/25 + 1;
		if (newQuartile < quartile) proposedScore = (quartile-1)*25;
		
		// update the score item
		scores.set(index,proposedScore);
		
		currentQuestionId = nextQuestionId;
		nextQuestionId = getNewQuestionId();
		ofy().save().entity(this);
	}
	
	Long getNewQuestionId() throws Exception {
		StringBuffer debug = new StringBuffer("NewQuestionId:");
		try {
			// Select a topic based on current topic scores
			Long topicId = null;
			int range = 0;
			for (Long tId : topicIds) {
				range += 100 - scores.get(topicIds.indexOf(tId));
			}
			Random random = new Random();
			if (range > 0) {
				int r = random.nextInt(range);
				range = 0;
				for (Long tId : topicIds) {
					range += 100 - scores.get(topicIds.indexOf(tId));
					if (r < range) {
						topicId = tId;
						break;
					}
				}
			} else topicId = topicIds.get(random.nextInt(topicIds.size()));
			
			// Gather question keys for types based on quartile score for the topic
			// Quartile 1 gets TF & MC, Q2 gets MC & FB, Q3 gets FB & CB, Q4 gets CB & NU
			int score = scores.get(topicIds.indexOf(topicId));
			int quartile = score/25 + 1;
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
		} catch (Exception e) {
			throw new Exception(e.getMessage() + debug.toString());
		}
	}


}
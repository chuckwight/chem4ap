package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;

@Entity
public class Score {    
	@Id	Long id;  // assignmentId
	@Parent Key<User> owner;
	
	List<Long> topicIds = new ArrayList<Long>();
	Map<Long,Integer> scores = new HashMap<Long,Integer>();  // scores 0-100 by topicId
	Map<Long, ArrayList<Key<Question>>> answeredQuestionKeys = new HashMap<Long,ArrayList<Key<Question>>>();
	Long currentQuestionId;
	Long nextQuestionId;
	
	Score() {}
	Score(String hashedId, Assignment a) {
		this.id = a.id;
		this.owner = key(User.class,hashedId);
		this.topicIds = a.topicIds;
		for (Long topicId : topicIds) {
			scores.put(topicId, 0);
			answeredQuestionKeys.put(topicId, new ArrayList<Key<Question>>());
		}
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
				this.answeredQuestionKeys.remove(tId);
			}
		}
		for (Long tId : a.topicIds) {  // add new data for topics that were added
			if (!this.topicIds.contains(tId)) {
				this.scores.put(tId,0);
				this.answeredQuestionKeys.put(tId,new ArrayList<Key<Question>>());
			}
		}
		this.topicIds = a.topicIds;
		ofy().save().entity(this).now();
	}

	void update(Question q, int qScore) {
		Long topicId = q.topicId;
		int quartile = this.scores.get(topicId)/25 + 1;
		int n = (21-3*quartile)/2; // averaging constant - range 9-4 
		int proposedScore = (120*qScore + n*scores.get(topicId))/(n+1);
		if (proposedScore > 100) proposedScore = 100;
		int newQuartile = proposedScore/25 + 1;
		if (newQuartile < quartile) proposedScore = (quartile-1)*25;
		scores.put(topicId,proposedScore);
		answeredQuestionKeys.get(topicId).add(key(q));
		ofy().save().entity(this);
	}
}
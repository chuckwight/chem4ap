/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.Serializable;
import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class UserReport implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	Date submitted;
			String userId;
			int stars;
			Long questionId;
			Integer parameter;
			String studentAnswer;
			String comments = "";
	
	UserReport() {}
	
	UserReport(String userId,Long questionId,Integer parameter,String studentAnswer,String comments) {
		this.userId = Util.hashId(userId);
		this.questionId = questionId;
		this.parameter = parameter;
		this.studentAnswer = studentAnswer;
		this.comments = comments;
		this.submitted = new Date();
	}
	
	public String view() {
		StringBuffer buf = new StringBuffer();
		buf.append("<FONT COLOR=RED>" + comments + "</FONT><br>");
		if (this.questionId>0) {			
			Question q = ofy().load().type(Question.class).id(this.questionId).safe();
			if (q.requiresParser()) q.setParameters(parameter);

			buf.append(q.printAllToStudents(studentAnswer,true,false) + "<br/>");
			
			buf.append("<a href=https://www.chem4ap.com/questions?UserRequest=EditQuestion&QuestionId=" + q.id + " target=_blank>Edit this question</a>");
/*			
			buf.append("The user's answer was: ");
			
			int j = 0;
			switch (q.type) {
			case "multiple_choice":
				j = studentAnswer.charAt(0)-'a';
				buf.append("<b>" + q.choices.get(j) + "</b><br/><br/>");
				break;
			case "checkbox":
				buf.append("<ul>");
				for (int i=0;i<studentAnswer.length();i++) {
					j = studentAnswer.charAt(i)-'a';
					buf.append("<li><b>" + q.choices.get(j) + "</b></li>");
				}
				buf.append("</ul>");
				break;
			default:
				buf.append("<b>" + studentAnswer + "</b><br/><br/>");
			}
*/
		}
		return buf.toString();
	}
}
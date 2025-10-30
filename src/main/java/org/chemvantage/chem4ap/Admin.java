package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/admin")
public class Admin extends HttpServlet {

	private static final long serialVersionUID = 1L;
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		String userId = "admin";
		User user = new User("https://"+request.getServerName(), userId);
		user.setIsChemVantageAdmin(true);
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		try {
			switch (userRequest) {
			default: 
				out.println(adminForm(user));
			}
		} catch (Exception e) {
			out.println("Chem4AP Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
	}
	
	String adminForm(User user) {
		StringBuffer buf = new StringBuffer(Util.head("Admin"));
		
		buf.append("<h1>Administration</h1>");
		
		// Show section with user reports, if any
		List<UserReport> reports = ofy().load().type(UserReport.class).list();
		int nReports = reports.size();
		if (nReports>0) {
			buf.append("<h2>User Feedback</h2>"
				+ "There are <a href='/feedback?UserRequest=ViewFeedback&sig=" + user.getTokenSignature() + "'>" + nReports + " new user reports.</a>");
		}
		return buf.toString() + Util.foot();
	}
}

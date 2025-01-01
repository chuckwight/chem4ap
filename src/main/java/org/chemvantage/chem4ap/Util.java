package org.chemvantage.chem4ap;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;

import com.google.cloud.ServiceOptions;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

@Entity
public class Util {
	@Id static Long id = 1L;
	private static double avgStars = 0;
	private static int nStarReports = 0;
	private static String HMAC256Secret;
	private static String reCaptchaSecret;
	private static String reCaptchaSiteKey;
	private static String salt;
	private static String announcement;
	private static String sendGridAPIKey;
	
	@Ignore static final String projectId = ServiceOptions.getDefaultProjectId();
	
	static Util u;
	
	static {
		try {  // retrieve values from datastore when a new software version is installed
			if (u == null) u = ofy().load().type(Util.class).id(1L).safe();
		} catch (Exception e) { // this will run only once when project is initiated
			u = new Util();
			ofy().save().entity(u);
		}
	}
	
	private Util() {}
	
	static String banner = "<div style='font-size:2em;font-weight:bold;color:#000080;'><img src='/images/CVLogo_thumb.png' alt='ChemVantage Logo' style='vertical-align:middle;width:60px;'> Chem4AP</div>";

	static void addStarReport(int stars) {
		avgStars = (avgStars*nStarReports + stars)/(nStarReports+1);
		nStarReports++;
		ofy().save().entity(u);
	}

	static double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(avgStars));
	}
	
	static String hashId(String userId) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] bytes = md.digest((userId + System.getenv("SALT")).getBytes(StandardCharsets.UTF_8));
        	StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
		} catch (Exception e) {
        	return null;
        }
	}
	
	static String head(String title) {
		return "<!DOCTYPE html><html lang='en'>\n"
				+ "<head>\n"
				+ "  <meta charset='UTF-8' />\n"
				+ "  <meta name='viewport' content='width=device-width, initial-scale=1.0' />\n"
				+ "  <meta name='description' content='Chem4AP is an LTI app for teaching and learning AP Chemistry.' />\n"
				+ "  <meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />\n"
				+ "  <link rel='icon' href='images/logo.png' />\n"
			//	+ "  <link rel='canonical' href='https://sage.chemvantage.org' />\n"
				+ "  <title>Chem4P" + (title==null?"":" | " + title) + "</title>\n"
				+ "  <link href='https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;800;900&family=Shantell+Sans:wght@300;400;500;600;700;800&display=swap' rel='stylesheet'/>\n"
				+ "  <link rel='stylesheet' href='/css/style.css'>\n"
				+ "  <!-- Google tag (gtag.js) --> "
				+ "  <script async src=\"https://www.googletagmanager.com/gtag/js?id=AW-942360432\"></script> "
				+ "  <script> window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'AW-942360432'); </script>"
				+ "</head>\n"
				+ "<body>\n";
	}
	
	static String foot() {
		return  "<footer><p><hr style='width:600px;margin-left:0' />"
				+ "<a style='text-decoration:none;color:#000080;font-weight:bold' href=/index.html>"
				+ "sage</a> | "
				+ "<a href=/terms_and_conditions.html>Terms and Conditions of Use</a> | "
				+ "<a href=/privacy_policy.html>Privacy Policy</a> | "
				+ "<a href=/copyright.html>Copyright</a></footer>\n"
				+ "</body></html>";
	}

	static String getHMAC256Secret() { 
		return HMAC256Secret; 
	}

	static String getReCaptchaSecret() {
		return reCaptchaSecret;
	}

	public static String getReCaptchaSiteKey() {
		return reCaptchaSiteKey;
	}

	static String getSalt() { 
		return salt; 
	}

	public static String getAnnouncement() { 
		return announcement; 
	}

	static String getSendGridKey() {
		return sendGridAPIKey;
	}
	
	static String getServerUrl() {
		if (projectId.equals("chem4ap")) return "www.chem4ap.com";
		else if (projectId.equals("dev-chem4ap")) return "https://dev-chem4ap.appspot.com";
		return null;
	}
	
	static void sendEmail(String recipientName, String recipientEmail, String subject, String message) 
			throws IOException {
		Email from = new Email("admin@chemvantage.org","ChemVantage LLC");
		if (recipientName==null) recipientName="";
		Email to = new Email(recipientEmail,recipientName);
		Content content = new Content("text/html", message);
		Mail mail = new Mail(from, subject, to, content);

		SendGrid sg = new SendGrid(sendGridAPIKey);
		Request request = new Request();
		request.setMethod(Method.POST);
		request.setEndpoint("mail/send");
		request.setBody(mail.build());
		Response response = sg.api(request);
		System.out.println(response.getStatusCode());
		System.out.println(response.getBody());
		System.out.println(response.getHeaders());
	}

}
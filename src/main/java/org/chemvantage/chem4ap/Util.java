package org.chemvantage.chem4ap;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class Util {
	@Id Long id;

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
}

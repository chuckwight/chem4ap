package org.chemvantage.chem4ap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;

@WebListener
public class ObjectifyWebListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent event) {
	  // Use the next line to connect to a backup (or other non-default) datastore
	  //final Datastore datastore = DatastoreOptions.newBuilder().setDatabaseId("backup1").build().getService();
	  final Datastore datastore = DatastoreOptions.newBuilder().build().getService();
	  ObjectifyService.init(new ObjectifyFactory(datastore));
    
	  // This is a good place to register your POJO entity classes.
	  ObjectifyService.register(APChemTopic.class);
	  ObjectifyService.register(APChemUnit.class);
	  ObjectifyService.register(Nonce.class);
	  ObjectifyService.register(Util.class);
	    
  }
	
  @Override
  public void contextDestroyed(ServletContextEvent event) {
  }
}
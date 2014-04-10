package org.codice.pubsub.examples;

import java.io.IOException;
import java.util.Map;

import javax.security.auth.login.LoginException;

import net.ser1.stomp.Client;
import net.ser1.stomp.Listener;

public class Subscriber {

	public static void main(String[] args) throws LoginException, IOException {
		// TODO Auto-generated method stub
	    String ip = null;
	    String id = null;
	    
	    if (args.length < 1){
	        ip = "localhost";
	    } else {
	        ip = args[0];
	    }
	    
	    if(args.length < 2){
	        id = "faf4e8493h389fh4398f3h0001";
	    } else {
	        id = args[1];
	    }
	    
		 Client c = new Client( ip, 61613, "admin", "password" );
		 c.subscribe( "/topic/result/" + id, new Listener() {
			    public void message( Map header, String body ) {
			      if (body.equals( "get-info" )) {
			        // Send some message to the clients with info about the server
			    	  System.out.println(body.getBytes().toString().substring(0, 40));
			      }
			      System.out.println("=============================================================================");
			      
			      System.out.println(body.substring(0, 75));
			    }
			  } );
	}

}

package org.codice.pubsub.examples;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.security.auth.login.LoginException;

import net.ser1.stomp.Client;

public class Publisher {

	public static void main(String[] args)  {
		// TODO Auto-generated method stub
		  Client c = null;
		  System.out.println("Args len: " + args.length);
		try {
		    if(args.length < 1){
		        c = new Client( "localhost", 61613,"admin","password" );
		    } else {
		        c = new Client( args[0], 61613,"admin","password" );
		    }
		} catch (LoginException le) {
			le.printStackTrace();
		} catch (IOException ie) {
			ie.printStackTrace();
		}
		
        //create file object
		File file = null;
		if(args.length < 2){
		    file = new File("C://temp//query.json");
		} else {
		    file = new File(args[1]);
		}
        
        BufferedInputStream bin = null;
        String strFileContents = null;  
                //create FileInputStream object
                FileInputStream fin = null;
				try {
					fin = new FileInputStream(file);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
               
                //create object of BufferedInputStream
                bin = new BufferedInputStream(fin);
               
                //create a byte array
                byte[] contents = new byte[1024];
               
                int bytesRead=0;
                
               
                try {
					while( (bytesRead = bin.read(contents)) != -1){
					       
					        strFileContents = new String(contents, 0, bytesRead);
					        System.out.print(strFileContents);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
     
		  c.send( "/topic/query/subscription", strFileContents );
		  c.disconnect();
	}

}

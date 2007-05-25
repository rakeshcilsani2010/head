package org.mifos.framework.persistence;

import static org.mifos.framework.persistence.DatabaseVersionPersistence.APPLICATION_VERSION;
import static org.mifos.framework.persistence.DatabaseVersionPersistence.FIRST_NUMBERED_VERSION;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class Downgrader {
	
	public static void main(String[] args) throws Exception {
		Downgrader downgrader = new Downgrader();
		downgrader.parse(args);
		downgrader.run(System.out);
	}

	String error;
	
	int downgradeTo;
	
	String jdbcUrl;
	String driver;
	String username;
	String password;
	
	public void parse(String[] args) {
		if (args.length < 5) {
			error = "Too few arguments.\n" +
				"Should be: downgradeTo jdbcUrl driver user password\n";
		}
		else if (args.length == 5) {
			String argument = args[0];
			try {
				downgradeTo = Integer.parseInt(argument);
			}
			catch (NumberFormatException e) {
				this.error = "Argument " + argument + " is not a number.\n";
				return;
			}
			
			jdbcUrl = args[1];
			driver = args[2];
			username = args[3];
			password = args[4];
		}
		else {
			this.error = "Excess argument " + args[5] + ".\n";
		}
	}

	private void run(PrintStream out) throws Exception {
		run(out, openConnection());
	}

	Connection openConnection() throws Exception {
		Class.forName(driver);
		return DriverManager.getConnection(jdbcUrl, username, password);
	}

	public void run(PrintStream out, Connection connection) 
	throws Exception {
		if (error != null) {
			out.print(error);
			return;
		}
		
		if (downgradeTo < FIRST_NUMBERED_VERSION) {
			out.print("Attempt to downgrade to " + downgradeTo + 
				" which is before " + FIRST_NUMBERED_VERSION + ".\n");
			return;
		}
		
		if (downgradeTo >= APPLICATION_VERSION) {
			out.print("Attempt to downgrade to " + downgradeTo +
				" which is after " + (APPLICATION_VERSION - 1) + ".\n");
			return;
		}

		DatabaseVersionPersistence persistence = 
			new DatabaseVersionPersistence(connection);
		
		int currentVersion = persistence.read();
		if (downgradeTo >= currentVersion) {
			out.print("Already at database version " + currentVersion + ".\n");
			return;
		}

		List<Upgrade> downgrades = 
			persistence.downgrades(downgradeTo, currentVersion);
		for (Upgrade downgrade : downgrades) {
			out.print("Downgrading to " + downgrade.downgradeTo() + "...");
			out.flush();
			downgrade.downgrade(connection);
			out.print("done.\n");
			out.flush();
		}
	}

}

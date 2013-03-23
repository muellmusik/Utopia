// ***** you must turn off time server updating in System Preferences for this to work
// launchd keeps it alive forever otherwise

NTP {
	
	// kill any from launchd as well as mine
	*quit {
		"sudo launchctl remove org.ntp.ntpd; sudo killAll ntpd".runInTerminal;
	}
	
	*restartExternalSync {
		"sudo launchctl load -w /System/Library/LaunchDaemons/org.ntp.ntpd.plist".runInTerminal;
	}

	
	// sync to addr, and then slave to it
	*client {|serverAddr|
		var file, path = "/tmp/SCntpclient.conf", string;
		file = File.new(path, "w");
		string = "# Point to our network's master time server\nserver " ++ serverAddr.ip ++"\nrestrict default ignore\nrestrict 127.0.0.1\nrestrict " ++ serverAddr.ip ++ " mask 255.255.255.255 nomodify notrap noquery";
		
		file.write(string);
		file.close;
		(("sudo ntpdate -u " ++ serverAddr.ip) ++ "; sudo ntpd -c " ++ path).runInTerminal;
		
	}
	
	// just sync
	*syncToServer {|serverAddr|
		("sudo ntpdate -u " ++ serverAddr.ip).runInTerminal;
	}
	
	// set me up as a server
	*server {
		var file, path = "/tmp/SCntpserver.conf", string;
		file = File.new(path, "w");
		string = "server 127.127.1.0 prefer\nfudge  127.127.1.0 stratum 10\n# Give localhost full access rights\nrestrict 127.0.0.1\n# Give machines on our network access to query us\nrestrict 192.168.1.0 mask 255.255.255.0 nomodify notrap";
		
		file.write(string);
		file.close;
		("sudo ntpd -c " ++ path).runInTerminal;
	}
	
}
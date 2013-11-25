

NMLRecord {

	var <>server, <recName, <keepUp;
	var nodeID, cmdPeriodFunc, prevFreeServers;
	var <recordBuf, <recording = false;

	*new { |server, recName, keepUp = true, nodeID = 15|
		^super.newCopyArgs(server ? Server.default, recName ? "SC_NetRec_", keepUp, nodeID).init
	}

	init {}

	record { |name|

		if(recordBuf.isNil) {
			server.bind {
				this.prepareForRecord(name);
				server.sync;
				this.record(name);
			}
		}{
			if(keepUp) { this.configureCmdPeriod };
			if(recording.not) {
				server.sendMsg("/s_new", "server-net-record", nodeID, 1, 0, "bufnum", recordBuf.bufnum);
				recording = true;
				if(keepUp.not) {
					CmdPeriod.doOnce {
						recording = false;
						if (recordBuf.notNil) { recordBuf.close {|buf| buf.freeMsg }; recordBuf = nil; }
					}
				}
			} {
				server.sendMsg("/n_run", nodeID, 1);
				recording = true;
			};
			"Recording: %\n".postf(recordBuf.path);
		};
	}

	stopRecording {
		if(recording) {
			recording = false;
			server.sendBundle(nil, ['/error', -1], ["/n_free", nodeID]);
			"Recording Stopped: %\n".postf(recordBuf.path);

			recordBuf.close({ |buf| buf.freeMsg });
			recordBuf = nil;
			if(keepUp) { this.cleanUpCmdPeriod };
		} {
			"Not Recording".warn
		};
	}

	fileEnding {
		if(thisProcess.platform.name == \windows) {
			^Main.elapsedTime.round(0.01) ++ "." ++ server.recHeaderFormat
		} {
			^Date.localtime.stamp ++ "." ++ server.recHeaderFormat
		}
	}

	prepareForRecord { arg name, recHeaderFormat, recSampleFormat;
		var path;
		recHeaderFormat = recHeaderFormat ? server.recHeaderFormat;
		recSampleFormat = recSampleFormat ? server.recSampleFormat;
		if(File.exists(thisProcess.platform.recordingsDir).not) {
			thisProcess.platform.recordingsDir.mkdir
		};

		path = thisProcess.platform.recordingsDir +/+ name ++ this.fileEnding;
		recordBuf = Buffer.alloc(server, 65536, server.recChannels,
			{ |buf| buf.writeMsg(path, recHeaderFormat, recSampleFormat, 0, 0, true) },
			server.options.numBuffers + 1 // prevent buffer conflicts by using reserved bufnum
		);
		recordBuf.path = path;
		// todo: clean this up (also in server) synth def may need to be flexible and removable
		SynthDef("server-net-record", { arg bufnum;
			DiskOut.ar(bufnum, In.ar(0, server.recChannels))
		}).send(server);
	}


	// this hack allows for keeping up the recording node despite cmd-period
	// note that theoretically this may change the state of CmdPeriod.freeServers
	// we try to clean up properly, but there is no guarantee for consistency.

	configureCmdPeriod {
		"reconfiguring cmd-period behaviour".postln;
		prevFreeServers = CmdPeriod.freeServers;
		CmdPeriod.freeServers = false;
		CmdPeriod.add(
			 // we need to replace this, because otherwise the server frees the root node
			cmdPeriodFunc = {
				Server.freeAllBut([server], CmdPeriod.freeRemote);
				server.sendMsg("/n_free", 1); // free only default group (1), not root group (0)
				server.sendMsg("/clearSched");
				server.volume.free;
				server.initTree;
			}
		);
	}

	cleanUpCmdPeriod {
		"recovering old cmd-period behaviour".postln;
		CmdPeriod.freeServers = prevFreeServers;
		CmdPeriod.remove(cmdPeriodFunc);
	}


}


NMLNetRecord : NMLRecord {
	var resp, addrBook, oscPath;
	var markers, <startTime;

	*new { |addrBook, server, recName, keepUp = true, nodeID = 15, oscPath = "/netRecord"|
		^super.newCopyArgs(server ? Server.default, recName ? "SC_NetRec_", keepUp, nodeID).init(addrBook, oscPath)
	}

	init { |argAddrBook, argOSCPath|
		addrBook = argAddrBook;
		oscPath = argOSCPath.asString;
		markers = Order.new;
		/*
		NetAddr.broadcastFlag = true;
		addr = NMLNetAddrMP("255.255.255.255", 57120 + (0..6));
		*/

		resp = [
			OSCFunc({ |msg|
				var remoteRecName = msg[1];
				if(this.recording.not) { this.record(remoteRecName) };
			}, oscPath ++ "/startRecording").fix;
			OSCFunc({ |msg|
				this.stopRecording;
			}, oscPath ++ "/stopRecording").fix;
			// todo do: when closing, write markers to file header?
			OSCFunc({ |msg|
				var time = this.rectime;
				var label = msg[1];
				markers.put(time, label);
			}, oscPath ++ "/addMark").fix;

		];

	}

	rectime {
		^startTime !? { Main.elapsedTime - startTime }
	}

	start {
		startTime = Main.elapsedTime;
		addrBook.sendAll(oscPath ++ "/startRecording", recName)
	}

	stop {
		startTime = nil;
		addrBook.sendAll(oscPath ++ "/stopRecording", recName)
	}

	mark { |label|
		addrBook.sendAll(oscPath ++ "/addMark", label)
	}

	free {
		resp.do(_.free);
		//addr.disconnect;
	}



}




+ Volume {

	free {
		ampSynth.release;
		ampSynth = nil;
	}

}

+ Server {

	*freeAllBut { arg serverList, evenRemote = false;
		if (evenRemote) {
			set.do { arg server;
				if ( server.serverRunning and: serverList.includes(server).not ) { server.freeAll }
			}
		} {
			set.do { arg server;
				if (server.isLocal and: { server.serverRunning } and: { serverList.includes(server).not }) { server.freeAll }
			}
		}
	}

}


NMLAbstractAuthenticator {

	authenticate {|citizen, successFunc, failureFunc| this.subclassResponsibility }

}

// convenience for when no authentication is required
NonAuthenticator : NMLAbstractAuthenticator {

	*authenticate {|citizen, successFunc, failureFunc| successFunc.value }

}

// uses a collection known to all participants as a challenge test
// use something large, like the text of War and Peace ;-)
// this could possibly have-a separate responder app, so that that could be created separately in centralised systems (e.g. Register/strant)
ChallengeAuthenticator : NMLAbstractAuthenticator {
	classvar <>timeOut = 5;
	var challenge, oscPath, challInds, challResponseOSCFunc;

	*new {|challenge, oscPath = '/challengeAuth'| ^super.newCopyArgs(challenge, oscPath).init; }

	init {
		// OSC sends Strings as Symbols, which is a minor pain, so convert Strings to Arrays of ASCII here
		if(challenge.isKindOf(String), { challenge = challenge.ascii });
		challInds = Pn(Plazy({Pshuf((0..(challenge.size - 1)), 1)}), inf).asStream;
		this.makeOSCFunc;
	}

	makeOSCFunc {
		challResponseOSCFunc = OSCFunc({|msg, time, addr|
			var inds;
			inds = msg[1..];
			addr.sendMsg(*([oscPath ++ "-challenge-reply"] ++ challenge[inds]));
		}, oscPath ++ "-challenge").fix;
	}

	free { challResponseOSCFunc.free; }

	// should there be a timeout?
	authenticate {|citizen, successFunc, failureFunc|
		var inds, vals, challReplyOSCFunc, testResult = false;

		#inds, vals = this.generateChallenge;

		challReplyOSCFunc = OSCFunc({|msg, time, addr|
			var challVals;
			challVals = msg[1..];
			testResult = challVals == vals;
			if(testResult, {
				successFunc.value;
			}, {
				"ChallengeAuthenticator challenge failed! Citizen: %\n".format(citizen).warn;
				failureFunc.value;
			});
		}, oscPath ++ "-challenge-reply", citizen.addr).oneShot;
		citizen.addr.sendMsg(*([oscPath ++ "-challenge"] ++ inds));

		//timeOut
		SystemClock.sched(timeOut, {
			if(testResult.not, {
				challReplyOSCFunc.free;
				"ChallengeAuthenticator challenge timed out! Citizen: %\n".format(citizen).warn;
				failureFunc.value;
			});
		});
	}

	generateChallenge {
		var inds, vals;
		inds = challInds.nextN(3);
		vals = challenge[inds];
		^[inds, vals];
	}
}
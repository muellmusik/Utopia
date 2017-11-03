
+ SynthDef {

	manipulateSynthDescForRepublic {
		var synthDesc = SynthDescLib.at(this.name);
		var ctl = synthDesc.controlNames;
		synthDesc !? {
			if(ctl.isNil or: { synthDesc.controlNames.includes("where").not }) {
				synthDesc.controlNames = synthDesc.controlNames.add("where");
				synthDesc.controls = synthDesc.controls.add(
					ControlName().name_('where').defaultValue_(0);
				);
				synthDesc.makeMsgFunc; // again
			};
		}
	}

	earmark { arg libname, completionMsg, keepDef = true;
		this.add(libname, completionMsg, keepDef);
		this.manipulateSynthDescForRepublic;
	}


	/*
	add { arg libname, completionMsg, keepDef = true;
		var	servers, desc = this.asSynthDesc(libname ? \global, keepDef);
		this.manipulateSynthDescForRepublic;
		if(libname.isNil) {
			servers = Server.allRunningServers
		} {
			servers = SynthDescLib.getLib(libname).servers
		};
		servers.do { |each|
			this.doSend(each.value, completionMsg.value(each))
		}
	}
	*/


}

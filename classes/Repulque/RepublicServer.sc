
RepublicServer : Server {

	var <serverRegistry, <serverMappings;
	var <>sortFunc;

	// need to update when hail detects changing topology
	serverRegistry_ { |sreg|
		var indices, keys;
		serverRegistry = sreg;
		// the first time, sort alphabetically
		if(serverMappings.isNil or: { sortFunc.notNil }) {
			keys = serverRegistry.keys.as(Array).sort(sortFunc);
			serverMappings = keys.collect { |x| serverRegistry[x] };
		} {
			// then just append
			sreg.pairsDo { |key, val|
				if(serverMappings.includes(val).not) {
					serverMappings = serverMappings.add(val)
				}
			};
			serverMappings.removeAllSuchThat { |x| sreg.includes(x).not }
		}
	}

	resolveAddr { |msg|
		var where, which, otherAddr;
	//	msg.postcs;
		where = msg.indexOf(\where);
		if(where.notNil) {
			which = msg[where + 1];
			if(which.isNil or: { which == 0 }) {
				^addr
			} {
				if(which.isNumber) {
					^serverMappings @@ which
				} {

					otherAddr = serverRegistry.at(which);
					if(otherAddr.notNil) {
						^otherAddr
					} {
						"RepublicServer: server name '%' not registered".format(which).warn;
					}
				}
			}
		};
		^addr
	}

	sendMsg { |... msg|
		this.resolveAddr(msg).sendMsg(*msg)
	}

	sendBundle { |time ... msgs|
		// use only first message for resolving it.
		// later, we could search until we have found the first one.
		this.resolveAddr(msgs.first).sendBundle(time, *msgs)
	}



}


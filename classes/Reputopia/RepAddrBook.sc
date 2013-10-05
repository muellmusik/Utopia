// extensions to AddrBook for a rewrite of Republic
// 

RepPeer : Peer { 
	var <server; 
	var <location; 
	
		// make them by hand now, should be automagic later
	makeServer { |port, options, clientID| 
		server = Server(name, NetAddr(addr.hostname, port), options, clientID);
	}
}

RepAddrBook : AddrBook {
	var <nameList; 
	var <groups; 
	
	
	init { super.init; nameList = List.new; groups = (); }
	
		// by default, add names in historical order
	add {|peer| super.add(peer); nameList.add(peer.name) }
	
	remove {|peer| super.remove(peer); nameList.remove(peer.name) }
	
	addGroup { |groupname, names| groups.put(groupname, names); }
	
	removeGroup { |groupname| groups.removeAt(groupname); }
	
	names { ^nameList }

		// always resolves to a single peer name, a flat list of names, or nil
	resolveWhere { |where| 
		// \all = sendAll 

			// expand list, flat to allow group substitution
		if (where.isKindOf(Array)) { 
			^where.collect(this.resolveWhere(_)).flat.reject(_.isNil)
		};
				// nil = me
		if (where.isNil) { ^me.name };
				// 
		if (where === \all) { ^nameList };
		
		if (where.isKindOf(Symbol)) { 
				// extant peer name: name
			if (dict[where].notNil) { ^where };
				// else might be a group
			if (groups[where].notNil) { 
				^groups[where].collect(this.resolveWhere(_)).reject(_.isNil);
			};	
				// none of the above - missing
			inform("AddrBook: Peer % is absent.".format(where)); 
			^nil
		};
		

			// index = nameList[index]
		if (where.isNumber) { ^nameList.wrapAt(where) };

//			// maybe later? 
//			// location (point) = nearest peer by location
//		if (where.isKindOf(Point)) { 
//			// find by location 
//		}
	}

	sendTo { |where ... msg| 
		this.resolveWhere(where).do { |name| 
				// only post for debugging
			dict.at(name).addr.postln.sendMsg(*msg); 
		};
	}

		// use deep down in events
	sendToServer { |where ... msg| 
		this.resolveWhere(where).do { |name| 
				// only post for debugging
			dict.at(name).server.addr.postln.sendMsg(*msg); 
		};
	}	
}

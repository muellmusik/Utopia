RepublicAddrBook : AddrBook {
	var <nameList; // this allows us to use integers as keys
	// todo: groups (should this be in addrbook?)

	add {|peer|
		var name = peer.name;
		super.add(peer);
		if(nameList.includes(name).not) { nameList = nameList.add(peer.name) };
	}

	send {|name ...msg|
		if(name == \all) {
			this.sendAll(*msg)
		} {
			name.asArray.do { |each|
				var peer;
				if(each.isInteger) { each = nameList.wrapAt(each) };
				peer = dict[each];
				// don't know if we need the warning, but good for now.
				if(peer.isNil or: { peer.online.not }) { "% is currently absent.\n".postf(peer.name) };
				peer.addr.sendMsg(*msg);
			}
		}
	}
}
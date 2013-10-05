First test for RepAddrBook, RepPeer: 

RepPeer has a server and a location
RepAddrBook has a nameList, groups, 
and can do republic style resolution of "where"
for peers and their servers.

	// make some
(
~rad = RepAddrBook.new;
~p1 = RepPeer(\p1, NetAddr("1.1.1.1", 57120), true);
~p2 = RepPeer(\p2, NetAddr("1.1.1.2", 57120), true);
~p3 = RepPeer(\p3, NetAddr("1.1.1.3", 57120), true);
~p4 = RepPeer(\p4, NetAddr("1.1.1.4", 57120), true);

~rad.add(~p1); 
~rad.add(~p2); 
~rad.add(~p3); 
~rad.add(~p4); 

	// add groups 
~rad.addGroup(\A, [\p1, \p2]);
~rad.addGroup(\B, [\p3, \p5]);	// p5 does not exist
~rad.addGroup(\C, [\p4, \p6]); 	//  p6 does not exist
)


~rad.nameList;

~rad.peers;

	// tests
~rad.resolveWhere(\p1);
~rad.resolveWhere([\p2]);
~rad.resolveWhere(0);
~rad.resolveWhere(3);
~rad.resolveWhere(1);
\~rad.resolveWhere(-1);
~rad.resolveWhere([-1, 1, \p3]);
~rad.resolveWhere([-1, 1, \p3, \otto]);

~rad.resolveWhere(\yohoNotThere);
~rad.resolveWhere(\A);

~rad.resolveWhere(\A);
~rad.resolveWhere(\C);
~rad.resolveWhere([0, \B, \p2]);

	//
~rad.sendTo(\p1, \yo);
~rad.sendTo([\p2], \yo);
~rad.sendTo(0, \yo);
~rad.sendTo(3, \yo);
~rad.sendTo(1, \yo);
~rad.sendTo(-1, \yo);
~rad.sendTo([-1, 1, \p3], \yo);
~rad.sendTo([-1, 1, \p3, \otto], \yo);

	// check groups
~rad.sendTo(\A, \yo);
~rad.sendTo(\C, \yo);
~rad.sendTo([0, \B, \p2], \yo);


	// this should be SharedServer later, so both 
	// separate and shared nodeIDs, bufnums etc are possible
(
~p1.makeServer(57109, nil, 1);
~p2.makeServer(57109, nil, 2);
~p3.makeServer(57109, nil, 3);
~p4.makeServer(57109, nil, 4);
)

	// test the sending to servers
~rad.sendToServer(\p1, \yo);
~rad.sendToServer(\A, \yo);
~rad.sendToServer([0, \B, \p2], \yo);



	// these can be made movable by shared data and a simple gui
(
~p1.location_(10@10);
~p1.location_(10@10);
~p1.location_(10@10);
~p1.location_(10@10);
)

	//
~rad.sendTo(\p1, \yo);
~rad.sendTo([\p2], \yo);
~rad.sendTo(0, \yo);
~rad.sendTo(3, \yo);
~rad.sendTo(1, \yo);
~rad.sendTo(-1, \yo);
~rad.sendTo([-1, 1, \p3], \yo);
~rad.sendTo([-1, 1, \p3, \otto], \yo);

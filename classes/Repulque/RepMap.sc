RepMap {
	var <addrBook;
	var <area, <lines;
	var <gui;

	*new { |book, area|

	}
}

RepMapGui {
	var <repmap, <w, <u;
	var <>locColor, <>selColor;
	var selectedLoc;
	var drawing;

	*new { |map|
		^super.new.init(map);
	}
	init { |map|
		repmap = map;
		w = Window("RepublicSpatial").front;
		u = UserView(w, w.bounds.moveTo(0,0));
		u.background = Color.grey(0.3);
		u.animate_(true);

		u.drawFunc = {
			if (repmap.notNil) {
				this.drawInfo;
				this.drawLocations;
				this.drawLines;
			};
		};
	}

	drawInfo {
		Pen.fillColor = Color.green;
		Pen.stringAtPoint("Click a  name to move it to its location;", 10@10);
		Pen.stringAtPoint("alt-click to start drawing a line;", 10@30);
		Pen.stringAtPoint("type backspace to clear last line.\n", 10@50);
	}

	drawLocations {
		repmap.locations.keysValuesDo { |name, loc|
			var locrect = Rect.aboutPoint(loc, 30, 20);
			var col = if (loc == selectedLoc, selColor, locColor);
			Pen.strokeColor = col; Pen.fillColor = col;
			Pen.addOval(locrect);
			Pen.stringCenteredIn(name.asString, locrect);
			Pen.stroke;
		};
	}

	drawLines {
		repmap.lines.do { |pair|
			pair;
			Pen.width = 1;
			Pen.fillColor = locColor;
			Pen.line(pair[0], pair[1]);
			Pen.stroke;
		}
	}
}
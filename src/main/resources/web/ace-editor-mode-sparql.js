var ace = require('ace');

exports.AceEditorModeSparql = {
	setupSparqlMode: function() {
		ace.define('ace/mode/sparql', [], function(require, exports, module) {
			var oop = require("ace/lib/oop");
			var TextMode = require("ace/mode/text").Mode;

			var MyHighlightRules = require("ace/mode/my_highlight_rules").MyHighlightRules;

			var Mode = function() {
				this.HighlightRules = MyHighlightRules;
			};
			oop.inherits(Mode, TextMode);

			(function() {
				this.lineCommentStart = "#";
			}).call(Mode.prototype);

			exports.Mode = Mode;
		});

		ace.define('ace/mode/my_highlight_rules', [], function(require, exports, module) {
			var oop = require("ace/lib/oop");
			var TextHighlightRules = require("ace/mode/text_highlight_rules").TextHighlightRules;

			var MyHighlightRules = function() {
				var keywordMapper = this.createKeywordMapper({

					"keyword": "PREFIX|SELECT|DISTINCT|FROM|NAMED|WHERE|LIMIT|GROUP BY|ORDER BY|VALUES" +
					"|FILTER|OPTIONAL|UNION|MINUS|GRAPH|CONSTRUCT|ASK|DESCRIBE"

				}, "text", false);

				this.$rules = {
					"start": [
						{
							regex: "\\w+\\b",
							token: keywordMapper
						},
						{
							token: "comment",
							regex: "#.*"
						}
					]
				};
				this.normalizeRules()
			};

			oop.inherits(MyHighlightRules, TextHighlightRules);
			exports.MyHighlightRules = MyHighlightRules;
		});
	}
};

if (typeof window === 'undefined') {
	module.exports = exports.AceEditorModeSparql;
	module.exports.AceEditorModeSparql = module.exports;
}

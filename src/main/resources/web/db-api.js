exports.DbApi = {





























	queryDb: function(p) {
		var sqlQueryStr = p.sqlQueryStr;
		var endpointBase = p['endpointBase'];  
		var token = p.token;
		var xhr = new XMLHttpRequest();
		var endpoint = '/query';
		if (endpointBase) {
			endpoint = endpointBase + endpoint;
		} else {
			if (window.location.hash) {
				var params = window.location.hash.substr(1)
					.split("&")
					.map(function(el) {
						return el.split("=");
					}).reduce(function(l, r) {
						l[r[0]] = r[1];
						return l;
					}, {});
				if (params['url']) {
					endpointBase = params['url'];
					endpoint = endpointBase + endpoint;
				}
			}
		}
		xhr.open('POST', endpoint);
		xhr.setRequestHeader('Content-Type', 'application/json');

		if (!window.sessionId) {
			window.sessionId = this.guid();
		}

		var promise = new Promise(function(resolve, reject) {
			xhr.onload = function() {
				if (xhr.status === 200) {
					var o = JSON.parse(xhr.responseText);

					resolve(o);
				} else {
					reject(undefined);
				}
			};
			if (token) {
				token.cancel = function() {
					xhr.abort();
					reject(new Error("Cancelled"));
					return promise;
				};
			}

			xhr.send(JSON.stringify({
				sessionId: window.sessionId,
				sqlQueryStr: sqlQueryStr
			}));
		});
		return promise;
	},

	guid: function() {
		function s4() {
			return Math.floor((1 + Math.random()) * 0x10000)
				.toString(16)
				.substring(1);
		}
		return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
			s4() + '-' + s4() + s4() + s4();
	},

	_makeExtraWhere: function (sqlParts) {
		var extraWhereFields = [];
		var extraWhere = [];
		var origin = sqlParts && sqlParts.origin;
		var originMajor = sqlParts && sqlParts.originMajor;
		var tripleCount = sqlParts && sqlParts.tripleCount;
		var edgeCover = sqlParts && sqlParts.edgeCover;
		var hypertreeWidth = sqlParts && sqlParts.hypertreeWidth;
		if (origin) {
			extraWhere.push('  "origin" ~~ ' + "'" + origin + "%'");
			extraWhereFields.push("origin");
		}
		if (originMajor) {
			extraWhere.push('  "originMajor" = ' + "'" + originMajor + "'");
			extraWhereFields.push("originMajor");
		}
		if (tripleCount) {
			var tripleCountSplit = tripleCount.split(",");
			if (tripleCountSplit.length > 1) {
				extraWhere.push("  \"tripleCount\" >= " + tripleCountSplit[0] +
					" AND \"tripleCount\" <= " + tripleCountSplit[1]);
			} else {
				extraWhere.push("  \"tripleCount\" = " + tripleCount);
			}
			extraWhereFields.push("tripleCount");
		}
		if (edgeCover) {
			var edgeCoverSplit = edgeCover.split(",");
			if (edgeCoverSplit.length > 1) {
				extraWhere.push("  \"edgeCover\" >= " + edgeCoverSplit[0] +
					" AND \"edgeCover\" <= " + edgeCoverSplit[1]);
			} else {
				extraWhere.push("  \"edgeCover\" = " + edgeCover);
			}
			extraWhereFields.push("edgeCover");
		}
		if (hypertreeWidth) {
			var hypertreeWidthSplit = hypertreeWidth.split(",");
			if (hypertreeWidthSplit.length > 1) {
				extraWhere.push("  \"hypertreeWidth\" >= " + hypertreeWidthSplit[0] +
					" AND \"hypertreeWidth\" <= " + hypertreeWidthSplit[1]);
			} else {
				extraWhere.push("  \"hypertreeWidth\" = " + hypertreeWidth);
			}
			extraWhereFields.push("hypertreeWidth");
		}
		return {
			extraWhereFields: extraWhereFields,
			extraWhere: extraWhere
		};
	},

	_makeParts: function (sqlParts, defaultFields) {
		var where = sqlParts ? sqlParts.where : undefined;
		var isWhere = where && Object.keys(where).length > 0;
		var extraWhere = this._makeExtraWhere(sqlParts);

		var whereStr = (isWhere ? [where].map(function(obj) {
			return Object.keys(obj).map(function(key) {
				return '  "' + key + '" = ' + obj[key]
			}).join(' AND\n');
		}) : []).concat(extraWhere.extraWhere).join(' AND\n');
		if (whereStr) {
			whereStr = "WHERE\n" + whereStr + "\n";
		}

		var quote = function(key) { return '"' + key + '"'; };
		var fields = defaultFields.map(quote).concat(isWhere ? [where].map(function(obj) {
			return Object.keys(obj).map(quote)
		}) : []).concat(extraWhere.extraWhereFields.map(quote));
		var fieldsStr = Array.from(new Set(fields)).join(",");




		return {
			fieldsStr: fieldsStr,
			whereStr: whereStr
		};
	},

	makeQuery: function(sqlParts) {
		var limit = sqlParts ? (sqlParts.limit ? sqlParts.limit : 10) : 10;
		var offset = sqlParts ? (sqlParts.offset ? sqlParts.offset : 0) : 0;
		var parts = this._makeParts(sqlParts, ["id", "origin", "queryStr"]);





















		return ("SELECT * FROM (\n--\n" +
			"SELECT :fieldStr\n" +
			"FROM \"Queries\"\n:whereStr" +
			"LIMIT :limit OFFSET :offset\n--\n" +
			") AS q LEFT JOIN LATERAL (\n" +
			"  SELECT count(id) as d_count FROM \"Duplicates\" AS d\n" +
			"  WHERE q.id = \"copyOfId\"\n" +
			") AS d ON true;")
				.replace(/:fieldStr/g, parts.fieldsStr)
				.replace(/:whereStr/g, parts.whereStr)
				.replace(/:limit/g, limit)
				.replace(/:offset/g, offset);
	},

	makeCountQuery: function(sqlParts) {
		var parts = this._makeParts(sqlParts, ["id", "origin", "queryStr"]);
		return "SELECT count(*) FROM \"Queries\" " +

			parts.whereStr + ";";
	},

	makeDataSetsQuery: function() {

		return 'SELECT\n  "originMajor",\n' +
			'  dupe + nodupe AS "total",\n' +
			'  nodupe AS "unique",\n' +
			'  valid AS "unique_valid"\n' +
			'FROM "OriginStats"\n' +
			'ORDER BY "order", "originMajor" ASC;';
	},

	makeIdQuery: function(id) {
		return "SELECT * FROM \"Queries\" WHERE id=" + id + ";";
	}
};

if (typeof window === 'undefined') {
	module.exports = exports.DbApi;
	module.exports.DbApi = module.exports;
}

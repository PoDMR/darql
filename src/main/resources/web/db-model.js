var columnGroups = {
	"Query types": [
		"select",
		"construct",
		"ask",
		"describe"
	],
	"Operators": [
		"and",
		"filter",
		"optional",
		"union",
		"graph",
		"minus",
		"exists",
		"notExists"
	],
	"Solution Modifiers": [
		"distinct",
		"limit",
		"ORDER__BY",
		"OFFSET"
	],
	"Aggregation": [
		"count",
		"sum",
		"avg",
		"min",
		"max",
		"HAVING",
		"GROUP__BY"
	],
	"Other Keywords" : [
		"assign",
		"bind",
		"data",
		"dataset",
		"service",
		"VALUES",
		"SAMPLE",
		"GROUP_CONCAT"
	],
	"Non-Keyword Features": [
		"regex",
		"teePredicate",
		"var_predicate",
		"subquery",


		"projection",
		"projectionUnsure",

		"askProjection",
		"askProjectionUnsure"
	],
	"Well-Designedness": [
		"wb",
		"uwd",
		"uwwd",


		"wdpt"
	],
	"Parsing": [
		"parseError"
	],
	"Shape": [
		"shapeless",
		"chain",
		"chainSet",
		"star",
		"circle",
		"utree",
		"uforest",
		"cycletree",
		"bicycle",
		"flower",
		"flowerSet"


	],





	"Conjunctive Queries": [
		"cq",
		"cq_f",
		"cq_fo"





	]
};
var columnNameMap = {
	"parseError": "parse error",
	"regex": "property path",
	"teePredicate": "T predicate",
	"var_predicate": "variable predicate",

























	"HAVING": "having",
	"GROUP__BY": "group by",
	"ORDER__BY": "order by",
	"OFFSET": "offset",
	"VALUES": "values",
	"SAMPLE": "sample",
	"GROUP_CONCAT": "group_concat",
	"wb": "well-behaved",
	"wd": "well-designed (wd)",
	"uwd": "union of wd",
	"wwd": "weakly wd (wwd)",
	"uwwd": "weakly wd (wwd)",


	"wdpt": "well-designed pattern tree",





	"chainSet": "set of chains",

	"circle": "cycle",
	"utree": "tree",
	"uforest": "forest",



	"flowerSet": "set of flowers",





	"cq": "cq",
	"cq_f": "cq with filters (cq_f)",
	"cq_fo": "cq_f with optionals",
	"afo": "",



	"opt_bad_interface": "bad interface"
};

exports.DbModel = {
	columnGroups: columnGroups,
	columnNameMap: columnNameMap
};

package com.gitlab.ctt.arq.analysis.aspect.db;

import fj.data.Either;
import org.apache.jena.query.Query;


@SuppressWarnings("WeakerAccess")
public class QueryRecord {
	public Either<Exception, Query> maybeQuery;
	public int id;
	public String origin;


	public String originMajor;  

	public String originMinor;  

	public int originLinum;  



	public byte[] hash;
	public String queryStr;

	public Boolean parseError;


	public Boolean regex;
	public Boolean teePredicate;
	public Boolean var_predicate;
	public Boolean bad_filter;
	public Boolean opt_bad_nesting;    
	public Boolean opt_bad_interface;  
	public Boolean projection;
	public Boolean projectionUnsure;
	public Boolean askProjection;
	public Boolean askProjectionUnsure;


	public Boolean wb;
	public Boolean uwd;
	public Boolean uwwd;
	public Boolean uwdComp;   
	public Boolean uwwdComp;  
	public Boolean wdpt;      

	public Boolean select;
	public Boolean construct;
	public Boolean ask;
	public Boolean describe;


	public Boolean distinct;
	public Boolean limit;
	public Boolean count;
	public Boolean sum;
	public Boolean avg;
	public Boolean min;
	public Boolean max;

	public Boolean afo;
	public Boolean afou;

	public Boolean and;
	public Boolean filter;
	public Boolean optional;
	public Boolean union;
	public Boolean graph;
	public Boolean subquery;
	public Boolean exists;
	public Boolean notExists;
	public Boolean service;
	public Boolean bind;
	public Boolean assign;
	public Boolean minus;
	public Boolean data;
	public Boolean dataset;


	public Boolean HAVING;
	public Boolean GROUP__BY;
	public Boolean ORDER__BY;
	public Boolean OFFSET;
	public Boolean VALUES;
	public Boolean SAMPLE;
	public Boolean GROUP_CONCAT;

	public Boolean cq;
	public Boolean cq_f;
	public Boolean cq_fo;
	public Boolean cq_fov;
	public Boolean cq_fox;

	public Boolean shapeless;

	public Boolean noNode;
	public Boolean singleNode;
	public Boolean noEdge;
	public Boolean singleEdge;
	public Boolean nonBranching;
	public Boolean selfLoops;
	public Boolean parallelEdges;
	public Boolean chainSet;
	public Boolean star;
	public Boolean circle;
	public Boolean utree;
	public Boolean uforest;
	public Boolean cycletree;
	public Boolean bicycle;
	public Boolean flower;
	public Boolean flowerSet;
	public Boolean spFlower;
	public Boolean spFlowerSet;

	public Boolean shapeless_nc;

	public Boolean noNode_nc;
	public Boolean singleNode_nc;
	public Boolean noEdge_nc;
	public Boolean singleEdge_nc;
	public Boolean nonBranching_nc;
	public Boolean selfLoops_nc;
	public Boolean parallelEdges_nc;
	public Boolean chainSet_nc;
	public Boolean star_nc;
	public Boolean circle_nc;
	public Boolean utree_nc;
	public Boolean uforest_nc;
	public Boolean cycletree_nc;
	public Boolean bicycle_nc;
	public Boolean flower_nc;
	public Boolean flowerSet_nc;
	public Boolean spFlower_nc;
	public Boolean spFlowerSet_nc;

	public Integer varCount;
	public Integer constCount;
	public Integer optNestCount;
	public Integer valuesCount;

	public Boolean hasNoVarPredicateReuse;
	public Boolean asRegularGraph;
	public Boolean isCyclic;
	public Boolean permit_service;
	public Boolean permit_bind;
	public Boolean permit_data;
	public Boolean permit_filter;

	public Boolean fca;
	public Boolean fca_htw;
	public Boolean treePattern;
	public Integer tripleSymbolCount;
	public Integer cl_min;
	public Integer cl_max;
	public Integer depth_max;
	public Integer degree_max;
	public Integer inner_degree_avg;
	public Integer split_tot;
	public Integer inner_tot;
	public Integer split_rel;
	public Integer inner_rel;

	public Boolean hasNoVarPredicateReuse_nc;
	public Boolean asRegularGraph_nc;
	public Boolean isCyclic_nc;
	public Boolean permit_service_nc;
	public Boolean permit_bind_nc;
	public Boolean permit_data_nc;
	public Boolean permit_filter_nc;

	public Boolean fca_nc;
	public Boolean treePattern_nc;
	public Integer cl_min_nc;
	public Integer cl_max_nc;
	public Integer depth_max_nc;
	public Integer degree_max_nc;
	public Integer inner_degree_avg_nc;
	public Integer split_tot_nc;
	public Integer inner_tot_nc;
	public Integer split_rel_nc;
	public Integer inner_rel_nc;








	public Integer tripleCount;
	public Double edgeCover;
	public Integer hypertreeWidth;
}

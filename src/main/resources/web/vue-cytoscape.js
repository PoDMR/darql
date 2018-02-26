var Vue = require('vue');
var cytoscape = require('cytoscape');

exports.VueCytoscape = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueCytoscape;
	module.exports.VueCytoscape = module.exports;
}
exports.VueCytoscape.install = function(Vue, options) {
	function addVertex(v1, color) {
		var cy = this;
		var existing = new Set(cy.nodes().map(function(x) {return x.id();}));
		if (!existing.has(v1)) {
			cy.add({group: "nodes", data: { id: ""+v1 }});
		}
		if (color) {






			cy.style().selector("node[id='"+v1+"']").style({
				'background-color': color
			}).update();
		}
	}

	function addEdge(v1, v2, label, color) {
		var cy = this;
		var en = v1 + "," + v2;
		if (label) {
			en += "," + label;
		}
		en = btoa(encodeURIComponent(en));

		var existing = cy.filter('edge[id = "' + en  +'"]').map(function(e) {
			return e.id();
		});
		if (existing.length <= 0) {
			cy.add({
				group: "edges",
				data: { id: en, label: label, source: ""+v1, target: ""+v2 }
			});
			if (color) {
				cy.style().selector("edge[id='"+en+"']").style({
					'target-arrow-color': color,
					'line-color': color
				}).update();
			}
		}
	}

	function invokeDownload() {
		var cy = this;
		var cyDom = cy.container();
		var canvas0 = cyDom.children[0].children[0];
		var canvas1 = cyDom.children[0].children[1];
		var canvas2 = cyDom.children[0].children[2];
		var canvasZ = document.createElement('canvas');
		canvasZ.width = canvas2.width;
		canvasZ.height = canvas2.height;
		var ctx = canvasZ.getContext('2d');
		ctx.fillStyle = "#EDFFED";
		ctx.fillRect(0, 0, canvas2.width, canvas2.height);
		ctx.drawImage(canvas0, 0, 0);
		ctx.drawImage(canvas1, 0, 0);
		ctx.drawImage(canvas2, 0, 0);
		var url = canvasZ.toDataURL();
		var link = document.createElement("a");
		link.download = "graph";
		link.href = url;
		link.click();
	}

	Vue.component('cytoscape', {
		template: '<div></div>',
		props: ['oninit'],
		data: function() {
			return {
				cy: Object
			}
		},
		watch: {
			'content': function(value) {
				if (this.value0 !== value) {
					this.editor.setValue(value, -1);  
				}
			}
		},
		methods: {
			resize: function() {
				this.cy.resize();
				this.cy.fit();
			}
		},
		mounted: function() {
			this.cy = cytoscape({
				container: this.$el,
				style: [
					{
						selector: 'node',
						style: {
							'background-color': '#666',
							'opacity': 0.8,
							'label': 'data(id)'
						}
					},

					{
						selector: 'edge',
						style: {
							'width': 3,
							'line-color': '#ccc',
							'curve-style': 'bezier',

							'target-arrow-color': '#ccc',
							'target-arrow-shape': 'triangle',

							'label': 'data(label)'
						}
					}
				]
			});
			this.cy.addVertex = addVertex;
			this.cy.addEdge = addEdge;
			this.cy.invokeDownload = invokeDownload;
			this['oninit'](this.cy);
		}
	});
};

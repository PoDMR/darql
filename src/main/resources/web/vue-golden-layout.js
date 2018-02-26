var Vue = require('vue');
var GoldenLayout = require('golden-layout');

exports.VueGoldenLayout = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueGoldenLayout;
	module.exports.VueGoldenLayout = module.exports;
}
VueGoldenLayout.install = function(Vue, options) {
	Vue.component('vgl-root', {
		template: '<div ref="goldenElement"><slot></slot></div>',
		data: function() { return {
			tgl: "test",
			components: []
		};},
		methods: {
			top: function() {
				return this;
			}
		},
		mounted: function() {
			var goldenElement = this.$refs['goldenElement'];
			var content = [];  
			this.config = {
				content: content,
				settings: {
					showCloseIcon: false,
					showPopoutIcon: false
				}
			};
			this.tgl = new GoldenLayout(this.config, $(goldenElement));
			var that = this;
			this.tgl.registerComponent('goldenTemplate', function(container, state) {
				var id = state.componentId;
				container.getElement().append(that.components[id].getElement());
				container.on('resize', function() {
					var el = container.getElement()[0];
					while (el) {
						if (el.__vue__) {
							if (el.__vue__.resize) {
								el.__vue__.resize()
							}
						}
						el = el.childNodes[0];
					}
				});
			});
			this.tgl.init();
			this.$children.forEach(function(child) {
				child.build();
			}, this);
			this.tgl.config.content.push(this.$children[0].getConfig());












			this.tgl.on('tabCreated', function(tab) {
				tab
					.closeElement
					.off('click')
					.click(function() {
						console.log(tab);

					});
			});







			$(window).resize(function() {
				that.tgl.updateSize();
			});
		}
	});

	function defineContainer(name, type) {
		Vue.component(name, {
			template: '<div style="display: none;"><slot></slot></div>',
			methods: {
				top: function() {
					return this.$parent.top();
				},
				build: function() {
					this.$children.forEach(function(child) {
						child.build();
					}, this);
				},
				getConfig: function() {
					return {

						type: type,
						content: this.$children.map(function(e) {
							return e.getConfig();
						})
					};
				}
			},
			mounted: function() {
			}
		});
	}
	defineContainer('vgl-column', 'column');
	defineContainer('vgl-row', 'row');
	defineContainer('vgl-stack', 'stack');

	var counter = 0;
	Vue.component('vgl-component', {
		template: '<div ref="componentElement" style="width: 100%; height: 100%;"><slot></slot></div>',
		props: ['title'],
		methods: {
			top: function() {
				return this.$parent.top();
			},
			build: function() {
			},
			getConfig: function() {
				return {

					type: 'component',
					componentName: 'goldenTemplate',
					title: this.title,
					componentState: { componentId: this.getId() }
				};
			},
			getId: function() {
				return this.id;
			},
			getElement: function() {

				return this.$refs['componentElement'];
			}
		},
		mounted: function() {
			this.id = counter++;
			this.top().components[this.id] = this;
		}
	});
};

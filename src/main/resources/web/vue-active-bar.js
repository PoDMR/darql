var Vue = require('vue');

exports.VueActiveBar = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueActiveBar;
	module.exports.VueActiveBar = module.exports;
}
exports.VueActiveBar.install = function(Vue, options) {
	Vue.component('active-bar', {

		template: '<div class="loading" :style="!this.active ? \'visibility: hidden\' : \'\'">\n' +
		'</div>',
		data: function() {
			return {
				active: false
			}
		},
		methods: {
			getStyle: function() {
				return this.active ?
					'' :
					'visibility: hidden';
			}
		}
	});
};

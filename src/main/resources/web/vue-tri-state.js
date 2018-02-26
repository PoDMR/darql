var Vue = require('vue');

exports.VueTriState = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueCytoscape;
	module.exports.VueCytoscape = module.exports;
}
exports.VueTriState.install = function(Vue, options) {
	Vue.component('tri-state', {

		template: '<fieldset>\n\t<legend>{{label}}</legend>\n\t<div class="switch-toggle switch-candy switch-candy-yellow">\n\t\t<input v-bind:id="ikey + \'-on\'" v-bind:name="ikey" type="radio"\n\t\t\t:checked="(boolval === true) ? \'checked\' : null">\n\t\t<label v-bind:for="ikey + \'-on\'" v-on:click="_onset(ikey, 1)">True</label>\n\n\t\t<input v-bind:id="ikey + \'-na\'" v-bind:name="ikey" type="radio"\n\t\t\t:checked="(boolval === undefined) ? \'checked\' : null">\n\t\t<label v-bind:for="ikey + \'-na\'" v-on:click="_onset(ikey, 0)">N/A</label>\n\n\t\t<input v-bind:id="ikey + \'-off\'" v-bind:name="ikey" type="radio"\n\t\t\t:checked="(boolval === false) ? \'checked\' : null">\n\t\t<label v-bind:for="ikey + \'-off\'" v-on:click="_onset(ikey, -1)">False</label>\n\n\t\t<a></a' +
		'>\n\t</div>\n</fieldset>',
		props: [
			'ikey',
			'label',
			'onset',

			'boolval'
		],





		watch: {
			'state': function(val) {


			}
		},
		methods: {
			_onset: function(key, val) {
				this['onset'](key, val);

			}
		},



	});
};

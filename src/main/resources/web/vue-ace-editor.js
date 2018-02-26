var Vue = require('vue');
var ace = require('ace');









exports.VueAceEditor = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueAceEditor;
	module.exports.VueAceEditor = module.exports;
}
exports.VueAceEditor.install = function(Vue, options) {


	Vue.component('ace-editor', {
		template: '<div :id="editorId" style="width: 100%; height: 100%;"><slot></slot></div>',
		props: ['editorId', 'content', 'lang', 'theme'],
		data: function() {
			return {
				editor: Object,
				value0: ''
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
				this.editor.resize();
			}
		},
		mounted: function() {
			this.lang = this.lang || 'text';
			this.theme = this.theme || 'solarized_light';
			this.editorId = this.editorId || 'editor';
			this.editor = ace.edit(this.$el);


			this.editor.getSession().setMode('ace/mode/' + this.lang);
			this.editor.setTheme('ace/theme/' + this.theme);
			var that = this;
			this.editor.on('change', function() {
				that.value0 = that.editor.getValue();
				that.$emit('change-content', that.editor.getValue());
			});

			this.editor.setOptions({
					enableBasicAutocompletion: true


			});
			this.editor.commands.bindKeys({
				"ctrl-l": null
			});

			this.editor.$blockScrolling = Infinity;
		}
	});
};

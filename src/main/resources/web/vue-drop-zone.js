var Vue = require('vue');

exports.VueDropZone = {};
if (typeof window === 'undefined') {
	module.exports = exports.VueDropZone;
	module.exports.VueDropZone = module.exports;
}
VueDropZone.install = function(Vue, options) {
	Vue.component('drop-zone', {
		props: ['ondrop'],
		template: '<div @dragover="_ondragover" @drop=_ondrop><slot></slot></div>',
		methods: {
			_ondragover: function(event) {
				event.preventDefault();
				event.dataTransfer.dropEffect = "all";
			},
			_ondrop: function(event) {
				event.preventDefault();
				var files = event.dataTransfer.files;
				var target = event.target;
				for (var i = 0, f; f = files[i]; i++) {
					var name = f.name;
					var reader = new FileReader();
					(function(that, name) {
						reader.onload = function(event) {
							var text = event.target.result;
							that.ondrop(target, name, text);
						};
					})(this, name);
					reader.readAsText(f);
				}
			}
		}
	});
};






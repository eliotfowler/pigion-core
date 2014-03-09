'use strict';


// Declare app level module which depends on filters, and services
angular.module('pigion', [
    'ngRoute',
    'pigion.filters',
    'pigion.services',
    'pigion.directives',
    'pigion.controllers',
    'ngResource',
    'flow'
]).
config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/view1', {templateUrl: '/assets/partials/partial1.html', controller: 'ShortenerController'});
    $routeProvider.when('/view2', {templateUrl: '/assets/partials/partial2.html', controller: 'FileUploadController'});
    $routeProvider.otherwise({redirectTo: '/view1'});
}]).
config(['flowFactoryProvider', function(flowFactoryProvider) {
    flowFactoryProvider.defaults = {
        target: '/files/upload',
        permanentErrors: [404, 500, 501],
        maxChunkRetries: 1,
        chunkRetryInterval: 5000,
        simultaneousUploads: 4
    };
    flowFactoryProvider.on('catchAll', function (event) {
        console.log('catchAll', arguments);
    });
}]);

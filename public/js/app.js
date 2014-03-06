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
  $routeProvider.when('/view2', {templateUrl: '/assets/partials/partial2.html', controller: 'MyCtrl2'});
  $routeProvider.otherwise({redirectTo: '/view1'});
}]);

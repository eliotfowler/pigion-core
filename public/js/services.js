'use strict';

/* Services */


// Demonstrate how to register services
// In this case it is a simple value service.
var pigion = angular.module('pigion.services', []);

pigion.factory('shortenerService', function($resource) {
    return $resource('/url/shorten');
});

'use strict';

var pigion = angular.module('pigion.controllers');

pigion.controller('FileListController', ['$scope', '$http', function($scope, $http) {
    $http({
        method: 'GET',
        url: '/files'
    }).success(function(data) {
        console.log("data", data);
        $scope.destinations = data;
    });
}]);
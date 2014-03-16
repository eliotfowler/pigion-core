'use strict';

var pigion = angular.module('pigion.controllers');

pigion.controller('FileUploadController', ['$scope', '$http', function($scope, $http) {
    console.log("here");
    $scope.$on('flow::fileAdded', function (event, $flow, flowFile) {
        event.preventDefault();//prevent file from uploading
    });

}]);
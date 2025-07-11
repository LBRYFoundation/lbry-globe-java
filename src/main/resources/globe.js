const globe = Globe();

var container = null;

window.addEventListener('load',function(){
    container = document.getElementById('globe');

    globe(container);
});

window.addEventListener('resize',function(event){
    globe.height(event.target.innerHeight);
    globe.width(event.target.innerWidth);
});

globe.backgroundImageUrl('//unpkg.com/three-globe/example/img/night-sky.png');
globe.bumpImageUrl('//unpkg.com/three-globe/example/img/earth-topology.png');
globe.globeImageUrl('earth.png');

globe.arcAltitude(0);
globe.arcColor(d => {
    return [`rgba(0, 255, 0, 0.1)`, `rgba(255, 0, 0, 0.1)`];
});
globe.arcStroke(0.1);

globe.onArcHover(hoverArc => {
    globe.arcColor(d => {
        const op = !hoverArc ? 0.1 : d === hoverArc ? 0.9 : 0.1 / 4;
        return [`rgba(0, 255, 0, ${op})`, `rgba(255, 0, 0, ${op})`];
    });
});

const POINT_ALTITUDE = {
    blockchain: 0.02,
    dht: 0.030,
    hub: 0.010,
};

const POINT_COLOR = {
    blockchain: '#0000FF',
    dht: '#FFFF00',
    hub: '#FF0000',
};

const POINT_RADIUS = {
    blockchain: 0.125,
    dht: 0.1,
    hub: 0.15,
};

globe.pointAltitude(point => POINT_ALTITUDE[point.type]);
globe.pointColor(function(point){
    var color = POINT_COLOR[point.type];
    if(point.ttl!==undefined){
        color += Math.round(point.ttl/300*256).toString(16).padStart(2,'0');
    }
    return color;
});
globe.pointLabel(point => point.label);
globe.pointRadius(point => POINT_RADIUS[point.type]);

globe.onPointClick(point => {
    console.log(point);
});

globe.onZoom((x) => {globe.controls().zoomSpeed = 2;});

var data = null;

function shuffleArray(array) {
    for (var i = array.length - 1; i >= 0; i--) {
        var j = Math.floor(Math.random() * (i + 1));
        var temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }
    return array;
}

function updatePointsData(points){
    var threeCache = {};

    var oldPointsData = globe.pointsData();
    for(var i=0;i<oldPointsData.length;i++){
        threeCache[oldPointsData[i].id] = oldPointsData[i];
    }

    var newPointsData = points;
    for(var i=0;i<newPointsData.length;i++){
        if(threeCache[newPointsData[i].id]){
            var newData = newPointsData[i];
            newPointsData[i] = threeCache[newPointsData[i].id];
            var newDataKeys = Object.keys(newData);
            for(var j=0;j<newDataKeys.length;j++){
                newPointsData[i][newDataKeys[j]] = newData[newDataKeys[j]];
            }
        }
    }

    var blockchainCount = 0;
    var dhtCount = 0;
    var hubCount = 0;
    for(var i=0;i<newPointsData.length;i++){
        if(newPointsData[i].type==='blockchain'){
            blockchainCount++;
        }
        if(newPointsData[i].type==='dht'){
            dhtCount++;
        }
        if(newPointsData[i].type==='hub'){
            hubCount++;
        }
    }
    document.getElementById('count-nodes-blockchain').textContent = blockchainCount;
    document.getElementById('count-nodes-dht').textContent = dhtCount;
    document.getElementById('count-nodes-hub').textContent = hubCount;
    document.getElementById('count-nodes-total').textContent = newPointsData.length;

    globe.pointsData(shuffleArray(newPointsData));
}

function updateGlobe(){
    fetch('/api')
        .then(resp => resp.json())
        .then(json => {
            data = json;
            updatePointsData(json.points);
            globe.arcsData(json.arcs);
        });
}

setInterval(updateGlobe,1_000);
updateGlobe();
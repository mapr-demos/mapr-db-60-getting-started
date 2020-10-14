#!/bin/bash
set -x
IMAGE="maprtech/dev-sandbox-container:latest"
INTERFACE="en0"
while [ $# -gt 0 ]
do
  case "$1" in
  -image) shift; IMAGE=$1;;
  -nwiterface) shift; INTERFACE=$1;;
  esac
  shift
done

which ipconfig
if [ $? -eq 0 ]; then
  IP=$(ipconfig getifaddr $INTERFACE)
else
  IP=$(ip addr show $INTERFACE | grep -w inet | awk '{ print $2}' | cut -d "/" -f1)
fi
clusterName="${clusterName:-"maprdemo.mapr.io"}"
hostName=$(echo ${clusterName} | cut -d '.' -f 1)

runMaprImage() {
    echo "Please enter the local sudo password for $(whoami)"
	sudo rm -rf /tmp/maprdemo
	sudo mkdir -p /tmp/maprdemo/hive /tmp/maprdemo/zkdata /tmp/maprdemo/pid /tmp/maprdemo/logs /tmp/maprdemo/nfs
	sudo chmod -R 777 /tmp/maprdemo/hive /tmp/maprdemo/zkdata /tmp/maprdemo/pid /tmp/maprdemo/logs /tmp/maprdemo/nfs

	PORTS='-p 9998:9998 -p 8042:8042 -p 8888:8888 -p 8088:8088 -p 9997:9997 -p 10001:10001 -p 8190:8190 -p 8243:8243 -p 2222:22 -p 4040:4040 -p 7221:7221 -p 8090:8090 -p 5660:5660 -p 8443:8443 -p 19888:19888 -p 50060:50060 -p 18080:18080 -p 8032:8032 -p 14000:14000 -p 19890:19890 -p 10000:10000 -p 11443:11443 -p 12000:12000 -p 8081:8081 -p 8002:8002 -p 8080:8080 -p 31010:31010 -p 8044:8044 -p 8047:8047 -p 11000:11000 -p 2049:2049 -p 8188:8188 -p 7077:7077 -p 7222:7222 -p 5181:5181 -p 5661:5661 -p 5692:5692 -p 5724:5724 -p 5756:5756 -p 10020:10020 -p 50000-50050:50000-50050 -p 9001:9001 -p 5693:5693 -p 9002:9002 -p 31011:31011'
	#export MAPR_EXTERNAL="0.0.0.0"
  #incase non-mac ipconfig command would not be found
  which ipconfig
  if [ $? -eq 0 ]; then
    export MAPR_EXTERNAL=$(ipconfig getifaddr $INTERFACE)
  else
    export MAPR_EXTERNAL=$(ip addr show $INTERFACE | grep -w inet | awk '{ print $2}' | cut -d "/" -f1)
  fi
	echo $MAPR_EXTERNAL

  if [ "${IMAGE}" == "maprtech/dev-sandbox-container:latest" ]; then docker pull ${IMAGE}; fi
	docker run -d --privileged -v /tmp/maprdemo/zkdata:/opt/mapr/zkdata -v /tmp/maprdemo/pid:/opt/mapr/pid  -v /tmp/maprdemo/logs:/opt/mapr/logs  -v /tmp/maprdemo/nfs:/mapr $PORTS -e MAPR_EXTERNAL -e clusterName -e isSecure --hostname ${clusterName} ${IMAGE} > /dev/null 2>&1

   # Check if docker container is started wihtout any issue
   sleep 5 # wait for docker container to start

    CID=$(docker ps -a | grep dev-sandbox-container | awk '{ print $1 }' )
    RUNNING=$(docker inspect --format="{{.State.Running}}" $CID 2> /dev/null)
    ERROR=$(docker inspect --format="{{.State.Error}}" $CID 2> /dev/null)

    if [ "$RUNNING" == "true" -a "$ERROR" == "" ]
    then
            echo "Developer Sandbox Container $CID is running.."
    else
            echo "Failed to start Developer Sandbox Container $CID. Error: $ERROR"
            exit
    fi
}

docker ps -a | grep dev-sandbox-container > /dev/null 2>&1
if [ $? -ne 0 ]
then
	STATUS='NOTRUNNING'
else
	echo "MapR sandbox container is already running."
	echo "1. Kill the earlier run and start a fresh instance"
	echo "2. Reconfigure the client and the running container for any network changes"
	echo -n "Please enter choice 1 or 2 : "
	read ANS
	if [ "$ANS" == "1" ]
	then
		CID=$(docker ps -a | grep dev-sandbox-container | awk '{ print $1 }' )
		docker rm -f $CID > /dev/null 2>&1
		STATUS='NOTRUNNING'
	else
		STATUS='RUNNING'
	fi
fi

if [ "$STATUS" == "RUNNING" ]
then
	# There is an instance of dev-sandbox-container. Check if it is running or not.
	CID=$(docker ps -a | grep dev-sandbox-container | awk '{ print $1 }' )
	RUNNING=$(docker inspect --format="{{.State.Running}}" $CID 2> /dev/null)
	if [ "$RUNNING" == "true" ]
	then
		# Container is running there.
		# Change the IP in /etc/hosts and reconfigure client for the IP Change
		# Change the server side settings and restart warden
		grep maprdemo /etc/hosts | grep ${IP} > /dev/null 2>&1
		if [ $? -ne 0 ]
		then
			echo "Please enter the local sudo password for $(whoami)"
			sudo sed -i '' '/maprdemo/d' /etc/hosts
			sudo  sh -c "echo  \"${IP}	${clusterName}  ${hostName}\" >> /etc/hosts"
			sudo sed -i '' '/maprdemo/d' /opt/mapr/conf/mapr-clusters.conf
            sudo /opt/mapr/server/configure.sh -c -C ${IP}  -N ${clusterName} > /dev/null 2>&1
			# Change the external IP in the container
			echo "Please enter the root password of the container 'mapr' "
			ssh root@localhost -p 2222 " sed -i \"s/MAPR_EXTERNAL=.*/MAPR_EXTERNAL=${IP}/\" /opt/mapr/conf/env.sh "
			echo "Please enter the root password of the container 'mapr' "
			ssh root@localhost -p 2222 "service mapr-warden restart"
		fi
	fi
	if [ "$RUNNING" == "false" ]
	then
		# Container was started earlier but is not running now.
		# Start the container. Change the client side settings
		# Change the server side settings
		docker start ${CID}
		echo "Please enter the local sudo password for $(whoami)"
		sudo sed -i '' '/maprdemo/d' /etc/hosts
		sudo sh -c "echo  \"${IP}	${clusterName}  ${hostName}\" >> /etc/hosts"
		sudo sed -i '' '/maprdemo/d' /opt/mapr/conf/mapr-clusters.conf
        sudo /opt/mapr/server/configure.sh -c -C ${IP}  -N ${clusterName} > /dev/null 2>&1
        # Change the external IP in the container
		echo "Please enter the root password of the container 'mapr' "
		ssh root@localhost -p 2222 " sed -i \"s/MAPR_EXTERNAL=.*/MAPR_EXTERNAL=${IP}/\" /opt/mapr/conf/env.sh "
		echo "Please enter the root password of the container 'mapr' "
        ssh root@localhost -p 2222 "service mapr-warden restart"
	fi
else
	# There is no instance of dev-sandbox-container running. Start a fresh container and configure client.
	runMaprImage

	sudo /opt/mapr/server/configure.sh -c -C ${IP}  -N ${clusterName} > /dev/null 2>&1
	sudo sed -i '' '/maprdemo/d' /etc/hosts
	sudo  sh -c "echo  \"${IP}	${clusterName}  ${hostName}\" >> /etc/hosts"

	echo
	echo "Docker Container is coming up...."
	echo "Mac Client has been configured with the docker container."
	echo
	echo "Please login to the container using (root password mapr): ssh root@localhost -p 2222 "
	echo "Login to MCS at https://localhost:8443 (This may take a few minutes..)"
fi

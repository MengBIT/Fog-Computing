package mengxu.simulation;

import mengxu.rule.AbstractRule;
import mengxu.simulation.event.AbstractEvent;
import mengxu.simulation.state.SystemState;
import mengxu.taskscheduling.*;
import mengxu.util.random.*;
import org.apache.commons.math3.random.RandomDataGenerator;

import java.util.List;
import java.util.PriorityQueue;

public class DynamicSimulation {

    private long seed;
    public RandomDataGenerator randomDataGenerator;
    private AbstractIntegerSampler numTasksSampler;
//    private AbstractRealSampler procTimeSampler;
    private AbstractRealSampler interReleaseTimeSampler;
    private AbstractRealSampler jobWeightSampler;
    private AbstractRealSampler workloadSampler;
    private AbstractRealSampler taskDataSampler;
    private AbstractRealSampler taskInputDataSampler;
    private AbstractRealSampler uploadBandwidthCloudSampler;
    private AbstractRealSampler downloadBandwidthCloudSampler;
    private AbstractRealSampler uploadBandwidthEdgeSampler;
    private AbstractRealSampler downloadBandwidthEdgeSampler;
    private AbstractRealSampler processingRateCloudSampler;
    private AbstractRealSampler processingRateEdgeSampler;
    private AbstractRealSampler processingRateDeviceSampler;

    protected AbstractRule sequencingRule;
    protected AbstractRule routingRule;
    protected SystemState systemState;

    private int numJobReleased;
    private int numJobsRecorded;


    //for multi-device simulator
    protected int warmupJobs;
    private int throughput = 0;
    //fzhang 3.6.2018  discard the individual(rule) can not complete the whole jobs well, take a long time (prefer to do part of each job)
    int beforeThroughput; //save the throughput value before updated (a job finished)
    int afterThroughput; //save the throughput value after updated (a job finished)
    int count = 0;

    protected PriorityQueue<AbstractEvent> eventQueue;

    public DynamicSimulation(long seed,
                              AbstractRule sequencingRule,
                              AbstractRule routingRule,
                              int numJobsRecorded,
                              int warmupJobs,
                              int numMobileDevice,
                              int numEdgeServer,
                              int numCloudServer,
                              AbstractIntegerSampler numTasksSampler,
//                              AbstractRealSampler procTimeSampler,
                              AbstractRealSampler workloadSampler,
                              AbstractRealSampler taskDataSampler,
                              AbstractRealSampler taskInputDataSampler,
                              AbstractRealSampler interReleaseTimeSampler,
                              AbstractRealSampler jobWeightSampler,
                              AbstractRealSampler uploadBandwidthCloudSampler,
                              AbstractRealSampler downloadBandwidthCloudSampler,
                              AbstractRealSampler uploadBandwidthEdgeSampler,
                              AbstractRealSampler downloadBandwidthEdgeSampler,
                              AbstractRealSampler processingRateCloudSampler,
                              AbstractRealSampler processingRateEdgeSampler,
                              AbstractRealSampler processingRateDeviceSampler,
                              boolean canMobileDeviceProcessTask) {
        this.seed = seed;
        this.sequencingRule = sequencingRule;
        this.routingRule = routingRule;
        this.numJobsRecorded = numJobsRecorded;
        this.systemState = new SystemState();
        this.eventQueue = new PriorityQueue<>();

        this.randomDataGenerator = new RandomDataGenerator();
        this.randomDataGenerator.reSeed(seed);
        this.numTasksSampler = numTasksSampler;
//        this.procTimeSampler = procTimeSampler;
        this.workloadSampler = workloadSampler;
        this.taskDataSampler = taskDataSampler;
        this.taskInputDataSampler = taskInputDataSampler;
        this.interReleaseTimeSampler = interReleaseTimeSampler;
        this.jobWeightSampler = jobWeightSampler;

        //modified 2021.08.02
        this.uploadBandwidthCloudSampler = uploadBandwidthCloudSampler;
        this.downloadBandwidthCloudSampler = downloadBandwidthCloudSampler;
        this.uploadBandwidthEdgeSampler = uploadBandwidthEdgeSampler;
        this.downloadBandwidthEdgeSampler = downloadBandwidthEdgeSampler;
        this.processingRateCloudSampler = processingRateCloudSampler;
        this.processingRateEdgeSampler = processingRateEdgeSampler;
        this.processingRateDeviceSampler = processingRateDeviceSampler;

        for (int i = 0; i < numMobileDevice; i++) {
            double processingRateDevice = this.processingRateDeviceSampler.next(this.randomDataGenerator);
            MobileDevice mobileDevice = new MobileDevice(i,processingRateDevice,
                    this.systemState,this.seed,
                    this.randomDataGenerator,
                    this.numTasksSampler, this.workloadSampler,
                    this.taskDataSampler, this.taskInputDataSampler,
                    this.interReleaseTimeSampler, this.jobWeightSampler,
                    this.uploadBandwidthCloudSampler, this.downloadBandwidthCloudSampler,
                    this.uploadBandwidthEdgeSampler, this.downloadBandwidthEdgeSampler,
                    this.processingRateCloudSampler, this.processingRateEdgeSampler,
                    this.sequencingRule,this.routingRule,
                    this.numJobsRecorded,warmupJobs);
            mobileDevice.setCanProcessTask(canMobileDeviceProcessTask);
            systemState.addMobileDevice(mobileDevice);
        }

        for(int i = 0; i < numEdgeServer; i++){
            double uploadBandwidth = this.uploadBandwidthEdgeSampler.next(this.randomDataGenerator);
            double downloadBandwidth = this.downloadBandwidthEdgeSampler.next(this.randomDataGenerator);
            double processingRate = this.processingRateEdgeSampler.next(this.randomDataGenerator);
            systemState.addServer(new Server(i, ServerType.EDGE,
                    uploadBandwidth, downloadBandwidth, processingRate));
        }

        for(int i = numEdgeServer; i < numEdgeServer + numCloudServer; i++){
            double uploadBandwidth = this.uploadBandwidthCloudSampler.next(this.randomDataGenerator);
            double downloadBandwidth = this.downloadBandwidthCloudSampler.next(this.randomDataGenerator);
            double processingRate = this.processingRateCloudSampler.next(this.randomDataGenerator);
            systemState.addServer(new Server(i, ServerType.CLOUD,
                    uploadBandwidth, downloadBandwidth, processingRate));
        }

        setup();

    }

    public DynamicSimulation(long seed,
                             AbstractRule sequencingRule,
                             AbstractRule routingRule,
                             int numJobsRecorded,
                             int warmupJobs,
                             int numMobileDevice,
                             int numEdgeServer,
                             int numCloudServer,
                             int minNumTasks,
                             int maxNumTasks,
//                             double minProcTime,
//                             double maxProcTime,
                             double minWorkload,
                             double maxWorkload,
                             double minTaskData,
                             double maxTaskData,
                             double minTaskInputData,
                             double maxTaskInputData,
                             double minProcessingRateCloud,
                             double maxProcessingRateCloud,
                             double minProcessingRateEdge,
                             double maxProcessingRateEdge,
                             double minProcessingRateDevice,
                             double maxProcessingRateDevice,
                             boolean canMobileDeviceProcessTask){
        this(seed,sequencingRule,routingRule,numJobsRecorded,warmupJobs,numMobileDevice,
                numEdgeServer,numCloudServer,new UniformIntegerSampler(minNumTasks, maxNumTasks),
                new UniformSampler(minWorkload, maxWorkload),
                new UniformSampler(minTaskData, maxTaskData),
                new UniformSampler(minTaskInputData, maxTaskInputData),
                new ExponentialSampler(),
                new TwoSixTwoSampler(), new BandwidthCloudSampler(), new BandwidthCloudSampler(),
                new BandwidthEdgeSampler(), new BandwidthEdgeSampler(),
                new UniformSampler(minProcessingRateCloud, maxProcessingRateCloud),
                new UniformSampler(minProcessingRateEdge, maxProcessingRateEdge),
                new UniformSampler(minProcessingRateDevice, maxProcessingRateDevice),
                canMobileDeviceProcessTask);
    }

    public DynamicSimulation(long seed,
                             AbstractRule sequencingRule,
                             AbstractRule routingRule,
                             int numJobsRecorded,
                             int warmupJobs,
                             int numMobileDevice,
                             int numEdgeServer,
                             int numCloudServer,
                             int minNumTasks,
                             int maxNumTasks,
                             boolean canMobileDeviceProcessTask){
        this(seed,sequencingRule,routingRule,numJobsRecorded,warmupJobs,numMobileDevice,
                numEdgeServer,numCloudServer,new UniformIntegerSampler(minNumTasks, maxNumTasks),
                new UniformSampler(500, 150000),
                new UniformSampler(1024, 1024*20),
                new UniformSampler(100, 300),
                new ExponentialSampler(),
                new TwoSixTwoSampler(),new BandwidthCloudSampler(), new BandwidthCloudSampler(),
                new BandwidthEdgeSampler(), new BandwidthEdgeSampler(),
                new UniformSampler(500, 1500),
                new UniformSampler(250, 500),
                new UniformSampler(10, 250),
                canMobileDeviceProcessTask);
    }


//    public void resetState() {
//        systemState.reset();
//        eventQueue.clear();
//        setup();
//    }

    public void reset() {
        systemState.reset();
//        resetState();
    }

    public void setup(){
        this.numJobReleased = 0;
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
//            int num = 0;
//            while(num<5){
                mobileDevice.generateJob();
//            mobileDevice.generateOneFixedJob();
//                num++;
//            }

        }
    }

    public void setSequencingRule(AbstractRule sequencingRule) {
        this.sequencingRule = sequencingRule;
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
            mobileDevice.setSequencingRule(sequencingRule);
        }

    }

//    public void setJobStates(int[] jobStates) { this.jobStates = jobStates; }

    public void setRoutingRule(AbstractRule routingRule) {
        this.routingRule = routingRule;
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
            mobileDevice.setRoutingRule(routingRule);
        }
//        //need to reset state as well, as the operationoptions associated
//        //with workcenters are chosen using this routing rule, so current
//        //values are outdated
//        resetState();
    }

    public AbstractRule getSequencingRule() {
        return sequencingRule;
    }

    public AbstractRule getRoutingRule() {
        return routingRule;
    }

    public boolean mobiledeviceHaveEvent(){
        boolean ref = false;
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
            if(mobileDevice.eventQueue.size()>0){
                ref = true;
                AbstractEvent nextEvent = mobileDevice.eventQueue.poll();
                this.eventQueue.add(nextEvent);
            }
        }
        return ref;
    }

    public void run(){
        if(this.systemState.getMobileDevices().size()==1){
            //single mobiledevice run!
            for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
                mobileDevice.run();
            }
        }
        else{
            //multiple mobiledevice run!
            while((mobiledeviceHaveEvent() || !eventQueue.isEmpty()) && throughput < numJobsRecorded){
                AbstractEvent nextEvent = eventQueue.poll();
//            systemState.setClockTime(nextEvent.getTime());
//            nextEvent.trigger(this);

                //fzhang 3.6.2018  fix the stuck problem
                beforeThroughput = throughput; //save the throughput value before updated (a job finished)

                systemState.setClockTime(nextEvent.getTime());
                nextEvent.trigger(nextEvent.getMobileDevice()); //nextEvent includes many different types of events

                afterThroughput = throughput; //save the throughput value after updated (a job finished)

                if(throughput > warmupJobs & afterThroughput - beforeThroughput == 0) { //if the value was not updated
                    count++;
                }

                //System.out.println("count "+count);
                if(count > 100000) {
                    count = 0;
                    systemState.setClockTime(Double.MAX_VALUE);
                    eventQueue.clear();
                    break;
                }


                //This is used to stop the bad run!!!
                //===================ignore busy machine here==============================
                //when nextEvent was done, check the numOpsInQueue
                if(nextEvent.getMobileDevice().isCanProcessTask()){
                    if(nextEvent.getMobileDevice().getQueue().size() > 150){
                        systemState.setClockTime(Double.MAX_VALUE);
                        eventQueue.clear();
                        break;
                    }
                }
                for (Server s: systemState.getServers()) {
                    if (s.numTaskInQueue() > 150) {
                        systemState.setClockTime(Double.MAX_VALUE);
                        eventQueue.clear();
                        break;
                    }
                }

            }
        }


//        System.out.println("Schedule complete!");
    }

    public void rerun() {
        //original
        //fzhang 2018.11.5 this is used for generate different instances in a generation.
        //if the replications is 1, does not have influence
        resetState();

        //reset(): reset seed value, will get the same instance
        //reset();
        run();
    }

    public void resetState() {
        systemState.reset();
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
            mobileDevice.eventQueue.clear();
        }
//        eventQueue.clear();
        setup();
    }


    public void rotateSeed() {//this is use for changing seed value in next generation
        //this only relates to generation
        for(MobileDevice mobileDevice :this.systemState.getMobileDevices()){
            mobileDevice.rotateSeed();
        }
        //System.out.println(seed);//when seed=0, after Gen0, the value is 10000, after Gen1, the value is 20000....
    }

    public SystemState getSystemState() {
        return systemState;
    }

    public double meanFlowtime() {
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("This is a bad run!");
            return Double.MAX_VALUE;
        }

        double value = 0.0;
        for (Job job : systemState.getJobsCompleted()) {
            value += job.getFlowTime();
        }

        return value/systemState.getJobsCompleted().size();
    }

    //todo:
    public double makespan(){
        if(systemState.getJobsCompleted().size() < numJobsRecorded){
//            System.out.println("This is a bad run!");
            return Double.MAX_VALUE;
        }
        double firstJobReleaseTime = Double.MAX_VALUE;
        double allJobComplete = 0;
        for (Job job : systemState.getJobsCompleted()) {
            if(job.getReleaseTime()<firstJobReleaseTime){
                firstJobReleaseTime = job.getReleaseTime();
            }
            if(job.getCompletionTime()>allJobComplete){
                allJobComplete = job.getCompletionTime();
            }
        }
        return allJobComplete-firstJobReleaseTime;
//        return allJobComplete;
    }

    public double objectiveValue(Objective objective) {
        switch (objective) {
            case MAKESPAN:
                return makespan();
            case MEAN_FLOWTIME:
                return meanFlowtime();
        }

        return -1.0;
    }

    public static DynamicSimulation standardFull(
            long seed,
            AbstractRule sequencingRule,
            AbstractRule routingRule,
            int numJobsRecorded,
            int warmupJobs,
            int numMobileDevice,
            int numEdgeServer,
            int numCloudServer,
            int minNumTasks,
            int maxNumTasks,
            double minWorkload,
            double maxWorkload,
            boolean canMobileDeviceProcessTask) {
        return new DynamicSimulation(seed,sequencingRule,routingRule,numJobsRecorded,warmupJobs,numMobileDevice,
                numEdgeServer,numCloudServer,new UniformIntegerSampler(minNumTasks, maxNumTasks),
                new UniformSampler(minWorkload, maxWorkload),
                new UniformSampler(1024, 1024*5),
                new UniformSampler(100, 300),
                new ExponentialSampler(),
                new TwoSixTwoSampler(), new BandwidthCloudSampler(), new BandwidthCloudSampler(),
                new BandwidthEdgeSampler(), new BandwidthEdgeSampler(),
                new UniformSampler(500, 1500),
                new UniformSampler(250, 500),
                new UniformSampler(10, 250),
                canMobileDeviceProcessTask);
    }

//    public static DynamicSimulation standardMissing(
//            long seed,
//            AbstractRule sequencingRule,
//            AbstractRule routingRule,
//            int numWorkCenters,
//            int numJobsRecorded,
//            int warmupJobs,
//            double utilLevel,
//            double dueDateFactor) {
//        return new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded,
//                warmupJobs,1, numWorkCenters, utilLevel, dueDateFactor, false);
//    }
}
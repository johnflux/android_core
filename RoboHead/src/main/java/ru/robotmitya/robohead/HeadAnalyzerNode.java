package ru.robotmitya.robohead;

import com.badlogic.gdx.math.Vector2;
import geometry_msgs.Twist;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import geometry_msgs.Vector3;
import ru.robotmitya.robocommonlib.AppConst;
import ru.robotmitya.robocommonlib.Log;
import ru.robotmitya.robocommonlib.MessageHelper;
import ru.robotmitya.robocommonlib.RoboState;
import ru.robotmitya.robocommonlib.Rs;

import java.lang.String;

/**
 * Created by dmitrydzz on 4/12/14.
 *
 */
public class HeadAnalyzerNode implements NodeMain {
    private static final float SMOOTH_FACTOR = 0.9f;

    private Publisher<std_msgs.String> mBodyPublisher;

    private final Vector2 mPosition = new Vector2();
    private Vector2 mSmoothedPosition;

    private int mControlMode = AppConst.Common.ControlMode.TWO_JOYSTICKS;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(AppConst.RoboHead.HEAD_ANALYZER_NODE);
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        mBodyPublisher = connectedNode.newPublisher(AppConst.RoboHead.BODY_TOPIC, std_msgs.String._TYPE);

        Subscriber<geometry_msgs.Twist> twistSubscriber = connectedNode.newSubscriber(AppConst.RoboHead.HEAD_TOPIC, geometry_msgs.Twist._TYPE);
        twistSubscriber.addMessageListener(createTwistMessageListener());

        Subscriber<std_msgs.String> commandSubscriber = connectedNode.newSubscriber(AppConst.RoboHead.CONTROL_MODE_TOPIC, std_msgs.String._TYPE);
        commandSubscriber.addMessageListener(createCommandMessageListener());
    }

    @Override
    public void onShutdown(Node node) {
    }

    @Override
    public void onShutdownComplete(Node node) {
    }

    @Override
    public void onError(Node node, Throwable throwable) {
    }

    private MessageListener<Twist> createTwistMessageListener() {
        return new MessageListener<geometry_msgs.Twist>() {
            @Override
            public void onNewMessage(geometry_msgs.Twist message) {
                Vector3 linear = message.getLinear();
                Vector3 angular = message.getAngular();
                float x = (float) -angular.getZ();
                float y = (float) -linear.getX();
                if (RoboState.getIsReverse()) {
                    y = -y;
                }
                if (x < -1) x = -1;
                else if (x > 1) x = 1;
                if (y < -1) y = -1;
                else if (y > 1) y = 1;
                mPosition.set(x, y);
                if (mSmoothedPosition == null) {
                    mSmoothedPosition = new Vector2(mPosition);
                } else {
                    mSmoothedPosition.lerp(mPosition, SMOOTH_FACTOR);
                }

                Log.messageReceived(HeadAnalyzerNode.this, String.format("x=%.3f, y=%.3f", mSmoothedPosition.x, mSmoothedPosition.y));

                if (mControlMode == AppConst.Common.ControlMode.TWO_JOYSTICKS) {
                    short horizontalDegree = getHorizontalDegree(mSmoothedPosition);
                    short verticalDegree = getVerticalDegree(mSmoothedPosition);
                    String horizontalCommand = MessageHelper.makeMessage(Rs.HeadHorizontalPosition.ID, horizontalDegree);
                    String verticalCommand = MessageHelper.makeMessage(Rs.HeadVerticalPosition.ID, verticalDegree);
                    publishCommand(horizontalCommand);
                    publishCommand(verticalCommand);
                } else if (mControlMode == AppConst.Common.ControlMode.ORIENTATION) {
                    Log.d(HeadAnalyzerNode.this, "+++");
                    //todo #14
                }
            }
        };
    }

    private MessageListener<std_msgs.String> createCommandMessageListener() {
        return new MessageListener<std_msgs.String>() {
            @Override
            public void onNewMessage(std_msgs.String message) {
                String messageBody = message.getData();

                Log.messageReceived(HeadAnalyzerNode.this, messageBody);

                final String identifier = MessageHelper.getMessageIdentifier(messageBody);
                final int value = MessageHelper.getMessageIntegerValue(messageBody);
                if (identifier.equals(Rs.Instruction.ID)) {
                    switch (value) {
                        case Rs.Instruction.CONTROL_MODE_TWO_JOYSTICKS:
                            mControlMode = AppConst.Common.ControlMode.TWO_JOYSTICKS;
                            break;
                        case Rs.Instruction.CONTROL_MODE_ORIENTATION:
                            mControlMode = AppConst.Common.ControlMode.ORIENTATION;
                            break;
                        case Rs.Instruction.CONTROL_MODE_CALIBRATE:
                            //...
                            break;
                    }
                }
            }
        };
    }

    private void publishCommand(final String command) {
        std_msgs.String message = mBodyPublisher.newMessage();
        message.setData(command);
        mBodyPublisher.publish(message);
        Log.messagePublished(this, mBodyPublisher.getTopicName().toString(), command);
    }

    private short getHorizontalDegree(final Vector2 pos) {
        double min = RoboState.getHeadHorizontalServoMinDegree();
        double max = RoboState.getHeadHorizontalServoMaxDegree();
        double result = ((1 - pos.x) * ((max - min) / 2)) + min;
        return (short)result;
    }

    private short getVerticalDegree(final Vector2 pos) {
        double min = RoboState.getHeadVerticalServoMinDegree();
        double max = RoboState.getHeadVerticalServoMaxDegree();
        double result = ((1 - pos.y) * ((max - min) / 2)) + min;
        return (short)result;
    }
}

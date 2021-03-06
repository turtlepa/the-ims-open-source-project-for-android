/*
 * This software code is (c) 2010 T-Mobile USA, Inc. All Rights Reserved.
 *
 * Unauthorized redistribution or further use of this material is
 * prohibited without the express permission of T-Mobile USA, Inc. and
 * will be prosecuted to the fullest extent of the law.
 *
 * Removal or modification of these Terms and Conditions from the source
 * or binary code of this software is prohibited.  In the event that
 * redistribution of the source or binary code for this software is
 * approved by T-Mobile USA, Inc., these Terms and Conditions and the
 * above copyright notice must be reproduced in their entirety and in all
 * circumstances.
 *
 * No name or trademarks of T-Mobile USA, Inc., or of its parent company,
 * Deutsche Telekom AG or any Deutsche Telekom or T-Mobile entity, may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" AND "WITH ALL FAULTS" BASIS
 * AND WITHOUT WARRANTIES OF ANY KIND.  ALL EXPRESS OR IMPLIED
 * CONDITIONS, REPRESENTATIONS OR WARRANTIES, INCLUDING ANY IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
 * NON-INFRINGEMENT CONCERNING THIS SOFTWARE, ITS SOURCE OR BINARY CODE
 * OR ANY DERIVATIVES THEREOF ARE HEREBY EXCLUDED.  T-MOBILE USA, INC.
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY
 * LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE
 * OR ITS DERIVATIVES.  IN NO EVENT WILL T-MOBILE USA, INC. OR ITS
 * LICENSORS BE LIABLE FOR LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES,
 * HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT
 * OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF T-MOBILE USA,
 * INC. HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * THESE TERMS AND CONDITIONS APPLY SOLELY AND EXCLUSIVELY TO THE USE,
 * MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE, ITS SOURCE OR BINARY
 * CODE OR ANY DERIVATIVES THEREOF, AND ARE SEPARATE FROM ANY WRITTEN
 * WARRANTY THAT MAY BE PROVIDED WITH A DEVICE YOU PURCHASE FROM T-MOBILE
 * USA, INC., AND TO THE EXTENT PERMITTED BY LAW.
 */

package javax.microedition.ims.android.msrp;

import android.os.RemoteException;
import android.util.Log;

import javax.microedition.ims.android.IReasonInfo;
import javax.microedition.ims.android.util.RemoteListenerHolder;
import javax.microedition.ims.common.Logger;
import javax.microedition.ims.core.IMSStackException;
import javax.microedition.ims.core.msrp.MSRPService;
import javax.microedition.ims.core.msrp.MSRPSession;
import javax.microedition.ims.core.msrp.filetransfer.FileDescriptor;
import javax.microedition.ims.core.msrp.listener.*;
import javax.microedition.ims.messages.utils.MsrpUtils;
import javax.microedition.ims.messages.wrappers.msrp.MsrpMessage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IMSessionImpl extends IIMSession.Stub {
    private static final String TAG = "Service - IMSessionImpl";

    private final MSRPSession msrpSession;

    private final RemoteListenerHolder<IIMSessionListener> listenerHolder = new RemoteListenerHolder<IIMSessionListener>(IIMSessionListener.class);

    private final MSRPService msrpService;

    private Map<String, Set<String>> requestRepository = new HashMap<String, Set<String>>();
    private Map<String, FileTransferEnv> fileRepository = new HashMap<String, FileTransferEnv>();

    private Map<FileRequestKey, String> fileRequestIdsMap = new HashMap<FileRequestKey, String>();


    private class FileTransferRunner implements MSRPSessionStartListener {
        private FileDescriptor fileDescriptor;
        private String requestId;

        public FileTransferRunner(FileDescriptor fileDescriptor, String requestId) {
            this.fileDescriptor = fileDescriptor;
            this.requestId = requestId;
        }


        public void onMSRPSessionStarted() {
            try {
                msrpSession.sendFile(fileDescriptor);
            } catch (IMSStackException e) {
                Logger.log(Logger.Tag.WARNING, "Can't send file " + fileDescriptor);
                e.printStackTrace();
            }
            cleanUp();
        }


        public void onMSRPSessionStartFailed(int reasonType, String reasonPhrase, int statusCode) {
            cleanUp();
        }

        private void cleanUp() {
            msrpSession.removeMSRPSessionStartListener(this);
        }
    }

    private class MSRPFileSendingListenersImpl implements MSRPFileSendingProgressListener, MSRPFileSendingListener {
        public void onBytesTransfered(FileDescriptor fileDescriptor, long bytesTransferred, long bytesTotal) {
            Logger.log(TAG, "MSRPFileTransferProgressListenerImpl.onBytesTransfered#started");

            String requestId = fileRequestIdsMap.get(FileRequestKey.getKey(msrpSession, fileDescriptor));

            notifyFileTransferProgress(requestId, fileDescriptor.getFileId(), bytesTransferred, bytesTotal);

            Logger.log(TAG, "MSRPFileTransferProgressListenerImpl.onBytesTransfered#finished");
        }

        public void onFileSent(final FileDescriptor fileDescriptor) {
            Logger.log(TAG, "MSRPFileSendingListenerImpl.onFileSent#started");

            String requestId = fileRequestIdsMap.get(FileRequestKey.getKey(msrpSession, fileDescriptor));

            notifyFileSent(requestId, fileDescriptor.getFileId());
            unSubscribe();
            Logger.log(TAG, "MSRPFileSendingListenerImpl.onFileSent#finished");
        }

        public void onFileSendFailed(FileDescriptor fileDescriptor, int failCode, String failReasonPhrase) {
            Logger.log(TAG, "MSRPFileSendingListenerImpl.onFileSendFailed#started");
            IReasonInfo reasonInfoImpl = new IReasonInfo(failReasonPhrase, 0, failCode);

            String requestId = fileRequestIdsMap.get(FileRequestKey.getKey(msrpSession, fileDescriptor));

            notifyFileSendFailed(requestId, fileDescriptor.getFileId(), reasonInfoImpl);
            unSubscribe();
            Logger.log(TAG, "MSRPFileSendingListenerImpl.onFileSendFailed#finished");
        }

        private void unSubscribe() {
            msrpSession.removeMSRPFileSendingListener(this);
            msrpSession.removeMSRPFileSendingProgressListener(this);
        }

        public void subscribe() {
            msrpSession.addMSRPFileSendingListener(this);
            msrpSession.addMSRPFileSendingProgressListener(this);
        }
    }

    /**
     * TODO subscribe to this listener
     */
    private class MSRPFileReceivingListenerImpl implements MSRPFileReceivingListener {

        public void onFileReceived(FileDescriptor fileDescriptor, String filePath) {
            Logger.log(TAG, "MSRPFileReceivingListenerImpl.onFileReceived#started");

            String requestId = fileRequestIdsMap.get(FileRequestKey.getKey(msrpSession, fileDescriptor));

            notifyFileReceived(requestId, fileDescriptor.getFileId(), filePath);
            cleanUp();
            Logger.log(TAG, "MSRPFileReceivingListenerImpl.onFileReceived#finished");
        }

        public void onFileReceiveFailed(FileDescriptor fileDescriptor, int failCode, String failReasonPhrase, boolean localy) {
            Logger.log(TAG, "MSRPFileReceivingListenerImpl.onFileReceiveFailed#started");
            IReasonInfo reasonInfoImpl = new IReasonInfo(failReasonPhrase, 0, failCode);

            String requestId = fileRequestIdsMap.get(FileRequestKey.getKey(msrpSession, fileDescriptor));

            notifyFileReceiveFailed(requestId, fileDescriptor.getFileId(), reasonInfoImpl);
            cleanUp();
            Logger.log(TAG, "MSRPFileReceivingListenerImpl.onFileReceiveFailed#finished");
        }

        private void cleanUp() {
            msrpSession.removeMSRPFileReceivingListener(this);
        }
    }

    private MSRPMessageSendingListener msrpMessageSendingListener = new MSRPMessageSendingListener() {

        public void onMessageSent(String messageId) {
            Logger.log(TAG, "msrpMessageSendingListener.onMessageSent#started");
            notifyMessageSent(messageId);
            Logger.log(TAG, "msrpMessageSendingListener.onMessageSent#finished");
        }


        public void onMessageSendFailed(String messageId, String reasonPhrase, int reasonType, int statusCode) {
            Logger.log(TAG, "msrpMessageSendingListener.onMessageSendFailed#started");
            notifyMessageSendFailed(messageId, reasonPhrase, reasonType, statusCode);
            Logger.log(TAG, "msrpMessageSendingListener.onMessageSendFailed#finished");
        }
    };

    private IncomingMSRPMessageListener incomingMSRPMessageListener = new IncomingMSRPMessageAdapter() {

        public void onMessageReceived(IncomingMSRPMessageEvent event) {
            Logger.log(TAG, "incomingMSRPMessageListener.onMessageReceived#started");
            notifyMessageReceived(event.getMsg());
            Logger.log(TAG, "incomingMSRPMessageListener.onMessageReceived#finished");
        }

        public void onComposingIndicatorReceived(IncomingMSRPMessageEvent event) {
            Logger.log(TAG, "incomingMSRPMessageListener.onComposingIndicatorReceived#started");
            notifyComposingIndicatorReceived(event.getMsg());
            Logger.log(TAG, "incomingMSRPMessageListener.onComposingIndicatorReceived#finished");
        }
    };


    public IMSessionImpl(final MSRPSession msrpSession, final MSRPService msrpService) {
        this.msrpSession = msrpSession;
        this.msrpService = msrpService;

        msrpService.addMSRPMessageSendingListener(msrpMessageSendingListener);
        msrpService.addIncomingMSRPMessageListener(msrpSession, incomingMSRPMessageListener);
    }


    public String getSessionId() throws RemoteException {
        return msrpSession.getIMSEntityId().stringValue();
    }


    public void sendMessage(IMessage message, boolean deliveryReport) throws RemoteException {
        Logger.log(TAG, "sendMessage#started");

        MsrpMessage msrpMessage = IMessageBuilderUtils.iMessageToMsrpMessage(message);

        Logger.log(TAG, "sendMessage#converted");

        try {
            msrpSession.sendMessage(msrpMessage);

        } catch (Throwable e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }

        Logger.log(TAG, "sendMessage#finish");
    }

    public void sendComposingIndicator(IMessage message) throws RemoteException {
        Logger.log(TAG, "sendComposingIndicator#started");

        MsrpMessage msrpMessage = IMessageBuilderUtils.iMessageToMsrpMessage(message);

        Logger.log(TAG, "sendComposingIndicator#converted");

        try {
            msrpSession.sendMessage(msrpMessage);

        } catch (Throwable e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }

        Logger.log(TAG, "sendComposingIndicator#finished");
    }


    public String sendFiles(IFileInfo[] files, boolean deliveryReport) throws RemoteException {
        Logger.log(TAG, "sendFiles#started");

        String requestId = MsrpUtils.generateFileRequestId();

        FileDescriptor[] fileDescrs = new FileDescriptor[files.length];
        for (int i = 0; i < files.length; i++) {
            fileDescrs[i] = IFileInfoConverter.convert(files[i]);
        }
        Logger.log(TAG, "sendFiles#converted");

        for (int i = 0; i < fileDescrs.length; i++) {
            try {
                msrpSession.addMSRPSessionStartListener(new FileTransferRunner(fileDescrs[i], requestId));
                new MSRPFileSendingListenersImpl().subscribe();
                msrpSession.addMSRPFileReceivingListener(new MSRPFileReceivingListenerImpl());


                msrpSession.openSendFileSession(fileDescrs[i]);

                //add file request id to repository
                fileRequestIdsMap.put(FileRequestKey.getKey(msrpSession, fileDescrs[i]), requestId);

                //add record to file repository
                fileRepository.put(fileDescrs[i].getFileId(), new FileTransferEnv(msrpSession, fileDescrs[i]));
                //add record to file transfer request repository
                Set<String> fileIds;
                if (requestRepository.containsKey(requestId)) {
                    fileIds = requestRepository.get(requestId);
                } else {
                    fileIds = new HashSet<String>();
                    requestRepository.put(requestId, fileIds);
                }
                fileIds.add(fileDescrs[i].getFileId());

                Logger.log(TAG, "sendFiles#sent file " + (i + 1) + "/" + fileDescrs.length);
            } catch (Throwable e) {
                Logger.log(TAG, e.getMessage());
                e.printStackTrace();
            }
        }

        Logger.log(TAG, "sendFiles#finished");
        return requestId;
    }

    public void cancelFileTransfer(String identifier) throws RemoteException {
        Logger.log(TAG, "cancelFileTransfer#started");
        if (requestRepository.containsKey(identifier)) {

            Set<String> fileIds = requestRepository.get(identifier);
            for (String id : fileIds) {
                FileTransferEnv fileTransferEnv = fileRepository.get(id);
                fileTransferEnv.getMsrpSession().cancelFileSending(fileTransferEnv.getFileDescriptor());
            }

        } else if (fileRepository.containsKey(identifier)) {
            FileTransferEnv fileTransferEnv = fileRepository.get(identifier);
            fileTransferEnv.getMsrpSession().cancelFileSending(fileTransferEnv.getFileDescriptor());
        }
        Logger.log(TAG, "cancelFileTransfer#finished");
    }


    public void close() throws RemoteException {
        Logger.log(TAG, "close#started");

        try {
            msrpSession.close();
            Logger.log(TAG, "close#sent");
        } catch (Throwable e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }

        Logger.log(TAG, "close#finish");
    }

    protected MSRPService getMsrpService() {
        return msrpService;
    }


    public void addListener(IIMSessionListener listener) throws RemoteException {
        if (listener != null) {
            listenerHolder.addListener(listener);
        }
    }


    public void removeListener(IIMSessionListener listener) throws RemoteException {
        if (listener != null) {
            listenerHolder.removeListener(listener);
        }
    }

    private void notifyMessageSent(String messageId) {
        Logger.log(TAG, "notifyMessageSent#started");
        try {
            listenerHolder.getNotifier().messageSent(messageId);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyMessageSent#finished");
    }

    private void notifyMessageSendFailed(String messageId, String reasonPhrase, int reasonType, int statusCode) {
        Logger.log(TAG, "notifyMessageSendFailed#started");
        IReasonInfo reasonInfoImpl = new IReasonInfo(reasonPhrase, reasonType, statusCode);

        try {
            listenerHolder.getNotifier().messageSendFailed(messageId, reasonInfoImpl);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyMessageSendFailed#finished");
    }

    private void notifyMessageReceived(MsrpMessage msrpMessage) {
        Logger.log(TAG, "notifyMessageReceived#started");
        IMessage messageImpl = IMessageBuilderUtils.msrpMessageToIMessage(msrpMessage);

        try {
            listenerHolder.getNotifier().messageReceived(messageImpl);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyMessageReceived#finished");
    }

    void notifySessionClosed(IReasonInfo reasonInfoImpl) {
        Logger.log(TAG, "notifySessionClosed#started");
        try {
            listenerHolder.getNotifier().sessionClosed(reasonInfoImpl);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifySessionClosed#finished");
    }

    private void notifyComposingIndicatorReceived(MsrpMessage msrpMessage) {
        Logger.log(TAG, "notifyComposingIndicatorReceived#started");

        String sender = msrpSession.getMsrpDialog().getRemoteParty();
        String messageBody = new String(msrpMessage.getBody());

        try {
            listenerHolder.getNotifier().composingIndicatorReceived(sender, messageBody);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyComposingIndicatorReceived#finished");
    }

    private void notifyFileReceived(String requestId, String fileId, String filePath) {
        Logger.log(TAG, "notifyFileReceived#started");
        try {
            listenerHolder.getNotifier().fileReceived(requestId, fileId, filePath);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileReceived#finished");
    }

    private void notifyFileReceiveFailed(String requestId, String fileId, IReasonInfo reason) {
        Logger.log(TAG, "notifyFileReceiveFailed#started");
        try {
            listenerHolder.getNotifier().fileReceiveFailed(requestId, fileId, reason);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileReceiveFailed#finished");
    }

    private void notifyFileSendFailed(String requestId, String fileId, IReasonInfo reason) {
        Logger.log(TAG, "notifyFileSendFailed#started");
        try {
            listenerHolder.getNotifier().fileSendFailed(requestId, fileId, reason);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileSendFailed#finished");
    }

    private void notifyFileSent(String requestId, String fileId) {
        Logger.log(TAG, "notifyFileSent#started");
        try {
            listenerHolder.getNotifier().fileSent(requestId, fileId);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileSent#finished");
    }

    /*
     * TODO call this method
     */

    private void notifyFileTransferFailed(String requestId, IReasonInfo reason) {
        Logger.log(TAG, "notifyFileTransferFailed#started");
        try {
            listenerHolder.getNotifier().fileTransferFailed(requestId, reason);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileTransferFailed#finished");
    }

    /*
     * TODO call this method
     */

    private void notifyFileTransferProgress(String requestId, String fileId, long bytesTransferred, long bytesTotal) {
        Logger.log(TAG, "notifyFileTransferProgress#started");
        try {
            listenerHolder.getNotifier().fileTransferProgress(requestId, fileId, bytesTransferred, bytesTotal);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyFileTransferProgress#finished");
    }

    /*
    * TODO call this method
    */

    private void notifyIncomingFilePushRequest(String requestId, FilePushRequestImpl filePushRequest) {
        Logger.log(TAG, "notifyIncomingFilePushRequest#started");
        try {
            listenerHolder.getNotifier().incomingFilePushRequest(filePushRequest);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifyIncomingFilePushRequest#finished");
    }

    /*
     * TODO call this method
     */

    private void notifySystemMessageReceived(MsrpMessage msrpMessage) {
        Logger.log(TAG, "notifySystemMessageReceived#started");
        IMessage messageImpl = IMessageBuilderUtils.msrpMessageToIMessage(msrpMessage);

        try {
            listenerHolder.getNotifier().systemMessageReceived(messageImpl);
        } catch (RemoteException e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();
        }
        Logger.log(TAG, "notifySystemMessageReceived#finished");
    }

}

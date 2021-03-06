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

package javax.microedition.ims.core;

import javax.microedition.ims.ServiceClosedException;

/**
 * The <code>Reference</code> is used for referring a remote endpoint to a
 * third party user or service.
 *
 *
 * </p><p>For detailed implementation guidelines and for complete API docs,
 * please refer to JSR-281 and JSR-235 documentation
 *
 *
 * @see CoreService#createReference(String, String, String, String)
 * @see Session#createReference(String, String)
 * @see CoreServiceListener
 * @see ReferenceListener
 */
public interface Reference extends ServiceMethod {

    /**
     * This state specifies that the <code>Reference</code> is created but not
     * started.
     */
    int STATE_INITIATED = 1;

    /**
     * This state specifies that the <code>Reference</code> has been started.
     */
    int STATE_PROCEEDING = 2;

    /**
     * This state specifies that the <code>Reference</code> has been accepted
     * and that the remote endpoint is referring to the third party.
     */
    int STATE_REFERRING = 3;

    /**
     * This state specifies that the <code>Reference</code> has been rejected
     * or terminated.
     */
    int STATE_TERMINATED = 4;

    /**
     * Sends the reference request to the remote endpoint.
     * <p/>
     * The <code>Reference</code> will transit to
     * <code>STATE_PROCEEDING</code> after calling this method.
     * <p/>
     * The <code>implicitSubscription</code> argument indicates if this
     * request should generate a implicit subscription. If <code>true</code>
     * the <code>Reference</code> transits to <code>STATE_REFERRING</code>
     * and the sender gets reports on the progress of the reference processing
     * at the remote. If <code>false</code>, the sender will only know if the
     * request was received. The <code>Reference</code> transits to
     * <code>STATE_TERMINATED</code> when the delivery report is received.
     *
     * @param implicitSubscription <code>true</code> if this
     *                             <code>Reference</code> should generate an implicit subscription,
     *                             <code>false</code> otherwise
     * @throws IllegalStateException  if the <code>Reference</code> is not in
     *                                <code>STATE_INITIATED</code>
     * @throws ServiceClosedException if the <code>Service</code> is closed
     * @see ReferenceListener#referenceDelivered(Reference)
     */
    void refer(boolean implicitSubscription) throws ServiceClosedException;

    /**
     * Returns the URI to refer to, optionally with a display name.
     *
     * @return the URI, optionally with a display name.
     */
    String getReferToUserId();

    /**
     * Returns the reference method to be used.
     *
     * @return the reference method
     */
    String getReferMethod();

    /**
     * Sets the replaces parameter of the Refer-To header to the given identity.
     * The application shall use the value of
     * <code>SessionDescriptor.getSessionId()</code>
     *
     * @param sessionId the identity of the Session to replace
     * @throws IllegalStateException    if the <code>Reference</code> is not in
     *                                  <code>STATE_INITIATED</code>
     * @throws IllegalArgumentException if the <code>sessionId</code> argument
     *                                  does not designate an established session, is <code>null</code>
     *                                  or an empty string
     * @see #getReplaces()
     * @see SessionDescriptor#getSessionId()
     */
    void setReplaces(String sessionId);

    /**
     * Returns the value of the replaces parameter of the Refer-To header.
     *
     * @return the value or <code>null</code> the value is not available
     * @see #setReplaces(String)
     */
    String getReplaces();

    /**
     * Accepts an server reference request.
     * <p/>
     * The <code>Reference</code> will transit to <code>STATE_REFERRING</code>
     * after calling this method.
     * <p/>
     * Note: After calling this method the application has the responsibility to
     * establish a connection to the third party depending on the refer method.
     *
     * @throws IllegalStateException  if the <code>Reference</code> is not in
     *                                <code>STATE_PROCEEDING</code>
     * @throws ServiceClosedException if the <code>Service</code> is closed
     */
    void accept() throws ServiceClosedException;

    /**
     * Rejects an server reference request.
     * <p/>
     * The <code>Reference</code> will transit to
     * <code>STATE_TERMINATED</code> after calling this method.
     *
     * @throws IllegalStateException  if the <code>Reference</code> is not in
     *                                <code>STATE_PROCEEDING</code>
     * @throws ServiceClosedException if the <code>Service</code> is closed
     */
    void reject() throws ServiceClosedException;

    /**
     * This method connects a service method with this reference and allows the
     * IMS engine to send notifications regarding this reference to the endpoint
     * that initiated this reference.
     *
     * @param serviceMethod the <code>ServiceMethod</code> to connect
     * @throws IllegalStateException    if the <code>Reference</code> is not in
     *                                  <code>STATE_REFERRING</code>
     * @throws IllegalArgumentException if the <code>serviceMethod</code>
     *                                  argument is <code>null</code>
     */
    void connectReferMethod(ServiceMethod serviceMethod);

    /**
     * Returns the current state of this <code>Reference</code>.
     *
     * @return the current state of this <code>Reference</code>
     */
    int getState();

    /**
     * Sets a listener for this <code>Reference</code>, replacing any
     * previous <code>ReferenceListener</code>. A <code>null</code> value
     * is allowed and has the effect of removing any existing listener.
     *
     * @param listener the listener to set, or <code>null</code>
     */
    void setListener(ReferenceListener listener);

}

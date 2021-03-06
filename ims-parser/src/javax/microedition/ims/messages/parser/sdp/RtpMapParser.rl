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

package javax.microedition.ims.messages.parser.sdp;

import javax.microedition.ims.common.Logger;
import javax.microedition.ims.messages.parser.ParserUtils;
import javax.microedition.ims.messages.wrappers.sdp.RtpMap;

public class RtpMapParser{

%%{
	machine rtpmap;
	alphtype char;

	action mark {
		m_Mark = p;
	}

	action set_payload {
		type =  ParserUtils.toNumber(arrayToString(m_Mark, p), 0, 10);
	}

	action set_clockrate {
		clockRate =  ParserUtils.toNumber(arrayToString(m_Mark, p), 0, 10);
	}

	action set_channels {
		channels =  ParserUtils.toNumber(arrayToString(m_Mark, p), 0, 10);
	}

	action set_name {
		name = arrayToString(m_Mark, p);
	}

	# ABNF core rules direct from RFC 2234

	ALPHA = alpha;
	BIT = "0" | "1";
	CHAR = ascii;
	CR = 0x0d;
	LF = 0x0a;
	CRLF = (CR LF | LF); # Also accept unix line endings
	CTL = cntrl | 0x7f;
	DIGIT = digit;
	DQUOTE = 0x22;
	HEXDIG = xdigit;
	HTAB = 0x09;
	SP = 0x20;
	VCHAR = 0x21..0x7E;
	WSP = SP | HTAB;
	LWSP = (WSP | CRLF WSP)*;
	POS_DIGIT = 0x31..0x39;

	alpha_numeric = ALPHA | DIGIT;

	number = digit+;

	encoding_parameters = number;

	clock_rate = number;

	valchar = (0x01..0x09 | 0x0B..0x0C | 0x0E..0xFF) - "/";

	encoding_name = valchar+;

	payload_type = number;

	rtpmap = payload_type >mark %set_payload
             WSP encoding_name >mark %set_name "/" 
                 clock_rate >mark %set_clockrate
             ("/" encoding_parameters >mark %set_channels)?;

	main := rtpmap;

}%%

%% write data;

	protected static String arrayToString(int mark, int p) {
		byte[] tmp = new byte[ p - mark ];
		System.arraycopy( data, mark, tmp, 0, p - mark);
		String result = new String( tmp );
		return result;
	}

	protected static byte[] data;
	
	public static RtpMap parse(String input) {
		try {
            data = input.getBytes("UTF-8");
        } catch (Exception e) {
            System.out.println("Unsupported encoding");
            return null;
        }

        int m_Mark = 0;
        int cs = 0;                  // Ragel keeps the state in "cs"
        int p = 0;                   // Current index into data is "p"
        int pe = data.length;        // Length of data
        int eof = pe;
        
    int type = -1;
	String name = null;
	int clockRate = -1;
	int channels = -1;

	%% write init;
	%% write exec;
        if (cs == rtpmap_error) {
            Logger.log(Logger.Tag.PARSER, "Your input did not comply with the grammar");
            Logger.log(Logger.Tag.PARSER, "SDP Parsing error at " + p);
            return null;
        }
        else if (cs < rtpmap_first_final) {
            Logger.log(Logger.Tag.PARSER, "rtpmap attribute incomplete");
            return null;
        }
        else {
            Logger.log(Logger.Tag.PARSER, "rtpmap parsed successfully!");
            return new RtpMap(type, name, clockRate, channels);

        }
    }

/*public static void main(String[] args) {
    String input = "99 H264/90000";
    RtpMap map = parse(input);
    Logger.log(Logger.Tag.PARSER,map);
}*/


}


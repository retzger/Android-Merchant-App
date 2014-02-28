package info.blockchain.merchant.tabsswipe;

import java.math.BigInteger;
import java.text.DecimalFormat;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View.OnFocusChangeListener;
import android.support.v4.view.ViewPager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.graphics.Bitmap;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.android.Contents;
import com.google.zxing.client.android.encode.QRCodeEncoder;

import com.google.bitcoin.uri.BitcoinURI;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import info.blockchain.api.ReceivePayments;
import info.blockchain.merchant.CurrencyExchange;
import info.blockchain.merchant.db.DBController;
import info.blockchain.merchant.R;
import info.blockchain.util.BitcoinAddressCheck;

public class PaymentFragment extends Fragment   {

	private View rootView = null;
	private ImageView imageView = null;
	private ProgressBar progressBar = null;
	private EditText posInput = null;
	private EditText posMessage = null;
	private Button bClear = null;
//	private Button bConfirm = null;
	private ImageButton imageConfirm = null;
    private TextView tvCurrency = null;
    private TextView tvCurrencySymbol = null;
    private TextView tvSendingAddress = null;
    private TextWatcher watcher = null;
    private SharedPreferences prefs = null;
    private SharedPreferences.Editor editor = null;
    
    private String strCurrency = null;
    private String strLabel = null;
    private String strMessage = null;
    private String strBTCReceivingAddress = null;
    private String input_address = null;
    private ContentValues dbVals = null;

    private boolean doBTC = false;
    private boolean doContinue = true;

    private Typeface font = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        editor = prefs.edit();

        rootView = inflater.inflate(R.layout.fragment_payment, container, false);
        
        font = Typeface.createFromAsset(getActivity().getAssets(), "fontawesome-webfont.ttf" );

        doBTC = prefs.getBoolean("use_btc", false);

        imageView = (ImageView)rootView.findViewById(R.id.qr);
        imageView.setImageResource(android.R.color.transparent);
        imageView.setVisibility(View.GONE);

        progressBar = (ProgressBar)rootView.findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        tvSendingAddress = (TextView)rootView.findViewById(R.id.sending_address);
        tvCurrencySymbol = (TextView)rootView.findViewById(R.id.currencySymbol);
        tvCurrencySymbol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	// toggle currency input mode
                doBTC = doBTC ? false : true;
                editor.putBoolean("use_btc", doBTC);
                editor.commit();
                
            	// swap amounts
                String swap = (String)tvCurrency.getText().subSequence(0, tvCurrency.getText().length() - 4);
                tvCurrency.setText(posInput.getText().toString());
                posInput.setText(swap);

            	// pop-up soft keyboard
                InputMethodManager inputMethodManager = (InputMethodManager)getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(posInput, InputMethodManager.SHOW_IMPLICIT);

            	// select entire EditText
                posInput.setSelection(0, posInput.getText().toString().length());

            	// display correct currency symbol
                setCurrencySymbol();

                //
                // in case someone toggle 15x fast ;)
                //
                System.gc();
            }
        });
        tvCurrencySymbol.setTypeface(font);

        tvCurrency = (TextView)rootView.findViewById(R.id.curr_display);

        posInput = (EditText)rootView.findViewById(R.id.posInput);
        posInput.setText("0");
        watcher = new POSTextWatcher();
        posInput.addTextChangedListener(watcher);
    	if(!doBTC) {
        	posInput.setSelection(posInput.getText().length());
    	}
        posInput.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        if (actionId == EditorInfo.IME_ACTION_DONE) {
                	if(BitcoinAddressCheck.isValid(strBTCReceivingAddress)) {
                    	Log.d("PaymentFragment", strBTCReceivingAddress);
                        makeNewPayment();
                        doContinue = false;
                    	imageConfirm.setImageResource(R.drawable.clear_button);
                    	imageConfirm.setBackgroundResource(R.drawable.balance_bg);
                    	tvCurrencySymbol.setClickable(false);
                	}
		        }
		        return false;
		    }
		});

        posMessage = (EditText)rootView.findViewById(R.id.note);
		posMessage.addTextChangedListener(new TextWatcher()	{
	        public void afterTextChanged(Editable s) { ; }
	        public void beforeTextChanged(CharSequence s, int start, int count, int after)	{ ; }
	        public void onTextChanged(CharSequence s, int start, int before, int count)	{ ; }
	    }); 

        imageConfirm = (ImageButton)rootView.findViewById(R.id.confirm);
        imageConfirm.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
            	
            	if(doContinue) {
                	if(BitcoinAddressCheck.isValid(strBTCReceivingAddress)) {
                    	Log.d("PaymentFragment", strBTCReceivingAddress);
                        makeNewPayment();
                        doContinue = false;
                    	imageConfirm.setImageResource(R.drawable.clear_button);
                    	imageConfirm.setBackgroundResource(R.drawable.balance_bg);
                    	tvCurrencySymbol.setClickable(false);
                	}
            	}
            	else {
                	initValues();
            	}

            }
        });

        initValues();
        
        return rootView;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if(isVisibleToUser) {

        	initValues();

        }
        else {
        	;
        }
    }

    @Override
    public void onResume() {
    	super.onResume();
     
    	initValues();

    }
    
    private Bitmap generateQRCode(String uri) {

        Bitmap bitmap = null;
        int qrCodeDimension = 380;

        QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(uri, null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), qrCodeDimension);

    	try {
            bitmap = qrCodeEncoder.encodeAsBitmap();
        } catch (WriterException e) {
            e.printStackTrace();
        }
    	
    	return bitmap;
    }

    private String generateURI() {

    	long longValue = setAmount();
        
        EditText posNote = (EditText)rootView.findViewById(R.id.note);
        strMessage = posNote.getText().toString();
//        tvSendingAddress.setText(input_address);
        
        dbVals = new ContentValues();
        dbVals.put("amt", longValue);
        dbVals.put("iad", input_address);
        dbVals.put("cfm", -1);
        dbVals.put("msg", strMessage);
        return BitcoinURI.convertToBitcoinURI(input_address, BigInteger.valueOf(longValue), strLabel, strMessage);
    }

    /** POSTextWatcher: handles input digit by digit and processed DEL (backspace) key
	 * 
	 */
    private class POSTextWatcher implements TextWatcher {

        public void afterTextChanged(Editable arg0) {
        	if(setAmount() > 0L) {
        		imageConfirm.setBackgroundResource(R.drawable.continue_bg);
        		imageConfirm.setClickable(true);
        	}
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) { ; }

        public void onTextChanged(CharSequence s, int start, int before, int count) { ; }

    }
    
    private void initValues() {

        if(prefs != null) {
        	strLabel = prefs.getString("receiving_name", "");
        	strBTCReceivingAddress = prefs.getString("receiving_address", "");
            strCurrency = prefs.getString("currency", "USD");
        }

        if(tvCurrency != null) {
        	tvCurrency.setText("0 " + ((!doBTC) ? " BTC" : (" " + strCurrency)));
        }
        
        setCurrencySymbol();
        
        if(tvCurrencySymbol != null) {
        	tvCurrencySymbol.setClickable(true);
        }

        if(tvSendingAddress != null) {
            tvSendingAddress.setText("");
        }

        if(posMessage != null) {
        	posMessage.setText("");
        }

        if(imageView != null) {
            imageView.setVisibility(View.GONE);
        }

        if(progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if(imageConfirm != null) {
            doContinue = true;
        	imageConfirm.setImageResource(R.drawable.continue_button);
    		imageConfirm.setBackgroundResource(R.drawable.balance_bg);
    		imageConfirm.setClickable(false);
        }
        
        if(posInput != null) {
        	posInput.setText("0");
        	posInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        	posInput.requestFocus();
            posInput.setSelection(0, posInput.getText().toString().length());
            InputMethodManager inputMethodManager = (InputMethodManager)  getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(posInput, InputMethodManager.SHOW_IMPLICIT);
        }

    }

    private void makeNewPayment() {

        imageView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        tvSendingAddress.setText(R.string.generating_qr);

    	final ReceivePayments receive_payments = new ReceivePayments(strBTCReceivingAddress);
    	
    	Log.d("makeNewPayments", receive_payments.getUrl());
    	
    	AsyncHttpClient client = new AsyncHttpClient();
        client.get(receive_payments.getUrl(), new AsyncHttpResponseHandler() {

        	@Override
            public void onSuccess(String response) {

        		Log.d("makeNewPayment", response);
                receive_payments.setData(response);
                receive_payments.parse();
                input_address = receive_payments.getInputAddress();
        		Log.d("makeNewPayment", input_address);

        		Bitmap bm = generateQRCode(generateURI());
        		Log.d("makeNewPayment", "bitmap is " +  ((bm == null) ? "null" :  "not null"));
                progressBar.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(bm);
                tvSendingAddress.setText(input_address);

            	posInput.setInputType(0);

        		// get timestamp (Unix time)
        		dbVals.put("ts", System.currentTimeMillis() / 1000);
        		// get fiat
        		String fiat_amount = null;
        		if(doBTC) {
        			fiat_amount = (String)tvCurrency.getText();
        		}
        		else {
        			fiat_amount = posInput.getEditableText().toString() + " " + strCurrency;
        		}
        		dbVals.put("famt", fiat_amount);

        		// write to DB
        		DBController pdb = new DBController(getActivity());
        		pdb.insertPayment(dbVals.getAsLong("ts"), dbVals.getAsString("iad"), dbVals.getAsLong("amt"), dbVals.getAsString("famt"), dbVals.getAsInteger("cfm"), dbVals.getAsString("msg"));
        		pdb.close();
            }

            @Override
            public void onFailure(Throwable arg0) {
        		Log.d("makeNewPayment", "failure:" + arg0.toString());
            }

        });

    }

    private long setAmount() {
    	double amount = 0.0;

    	if(doBTC) {
    		try {
            	amount = Double.valueOf(posInput.getText().toString().length() == 0 ? "0" : posInput.getText().toString());
    		}
    		catch(Exception e) {
    			amount = 0.0;
    		}
    	}
    	else {
    		amount = xlatFiat2BTC(posInput.getText().toString().length() == 0 ? "0" : posInput.getText().toString());
    	}

    	double value = Math.round(amount * 100000000.0);
    	long longValue = (Double.valueOf(value)).longValue();
        if(!doBTC) {
        	tvCurrency.setText(BitcoinURI.bitcoinValueToPlainString(BigInteger.valueOf(longValue)) + " BTC");
        }
        else {
        	String amt = Double.toString(xlatBTC2Fiat(posInput.getText().toString()));
        	if(amt.equals("0.0")) {
        		amt = "0";
        	}
        	tvCurrency.setText(amt + " " + strCurrency);
        }
        
        return longValue;
    }

    private void setCurrencySymbol() {
        if(tvCurrencySymbol != null) {
            if(doBTC) {
            	tvCurrencySymbol.setText(R.string.bitcoin_currency_symbol);
            }
            else if(strCurrency.equals("CNY")) {
            	tvCurrencySymbol.setText("¥");
            }
            else if(strCurrency.equals("EUR")) {
            	tvCurrencySymbol.setText("€");
            }
            else if(strCurrency.equals("GBP")) {
            	tvCurrencySymbol.setText("£");
            }
            else if(strCurrency.equals("JPY")) {
            	tvCurrencySymbol.setText("¥");
            }
            else {
            	tvCurrencySymbol.setText("$");
            }
        }
    }

    private double xlatBTC2Fiat(String strAmount) {

    	if(strAmount.length() < 1) {
    		strAmount = "0";
    	}
    	if(strAmount.equals(".")) {
    		strAmount = "0";
    	}

    	double amount = 0;

		if(strCurrency.equals("EUR")) {
        	amount = Double.valueOf(strAmount) * CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("EUR");
		}
		else if(strCurrency.equals("GBP")) {
        	amount = Double.valueOf(strAmount) * CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("GBP");
		}
		else if(strCurrency.equals("JPY")) {
        	amount = Double.valueOf(strAmount) * CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("JPY");
		}
		else if(strCurrency.equals("CNY")) {
        	amount = Double.valueOf(strAmount) * CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("CNY");
		}
		else {
        	amount = Double.valueOf(strAmount) * CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("USD");
		}
		
		return amount;
    }
    
    private double xlatFiat2BTC(String strAmount) {
    	
    	if(strAmount.length() < 1) {
    		strAmount = "0";
    	}
    	if(strAmount.equals(".")) {
    		strAmount = "0";
    	}
    	
    	double amount = 0;

		if(strCurrency.equals("EUR")) {
        	amount = Double.valueOf(strAmount) / CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("EUR");
		}
		else if(strCurrency.equals("GBP")) {
        	amount = Double.valueOf(strAmount) / CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("GBP");
		}
		else if(strCurrency.equals("JPY")) {
        	amount = Double.valueOf(strAmount) / CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("JPY");
		}
		else if(strCurrency.equals("CNY")) {
        	amount = Double.valueOf(strAmount) / CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("CNY");
		}
		else {
        	amount = Double.valueOf(strAmount) / CurrencyExchange.getInstance(getActivity()).getCurrencyPrice("USD");
		}
		
		return amount;
    }

    private void confirmPurchase() {

    	final ReceivePayments receive_payments = new ReceivePayments(strBTCReceivingAddress);
    	
    	AsyncHttpClient client = new AsyncHttpClient();
        client.get(receive_payments.getUrl(), new AsyncHttpResponseHandler() {

        	@Override
            public void onSuccess(String response) {

                receive_payments.setData(response);
                receive_payments.parse();
                input_address = receive_payments.getInputAddress();

                imageView.setImageBitmap(generateQRCode(generateURI()));
            }

            @Override
            public void onFailure(Throwable arg0) {
//        		Log.d(TAG, "failure:" + arg0.toString());
            }

        });

    }

}
